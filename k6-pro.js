import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

/**
 * =========================
 * CONFIG (via ENV)
 * =========================
 * Ex:
 *  k6 run -e BASE_URL=http://localhost:8080 -e PROFILE=poc-loom k6-professional.js
 */
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ENDPOINT = __ENV.ENDPOINT || "/v1/gateway/process";

// só para tagging/log (não muda chamada)
const PROFILE = __ENV.PROFILE || "poc-loom";

// Distribuição de tráfego (pesos)
const W_FAST = parseInt(__ENV.W_FAST || "70", 10);     // 70%
const W_MED  = parseInt(__ENV.W_MED  || "20", 10);     // 20%
const W_SLOW = parseInt(__ENV.W_SLOW || "8", 10);      // 8%  (tende a timeout)
const W_ERR  = parseInt(__ENV.W_ERR  || "2", 10);      // 2%  (tende a erro)

// Valores do seu mock externo
// timeout-after-value (default no projeto: 700)
// error-after-value   (default no projeto: 900)
const VALUE_FAST = parseInt(__ENV.VALUE_FAST || "200", 10); // normalmente responde
const VALUE_MED  = parseInt(__ENV.VALUE_MED  || "600", 10); // perto do limite
const VALUE_SLOW = parseInt(__ENV.VALUE_SLOW || "800", 10); // deve estourar timeout (>=700)
const VALUE_ERR  = parseInt(__ENV.VALUE_ERR  || "950", 10); // deve lançar erro (>=900)

// Payload “grande” (~200 campos)
const BIG_FIELDS = parseInt(__ENV.BIG_FIELDS || "200", 10);
const BIG_STRING_SIZE = parseInt(__ENV.BIG_STRING_SIZE || "40", 10);

// Timeouts do lado do k6 (pra não prender VU quando sua API travar)
const K6_REQ_TIMEOUT = __ENV.K6_REQ_TIMEOUT || "2s";

// Pausa entre iterações (simula think time)
const THINK_TIME = parseFloat(__ENV.THINK_TIME || "0.02"); // 20ms

/**
 * =========================
 * MÉTRICAS PROFISSIONAIS
 * =========================
 */
const fallbackRate = new Rate("fallback_rate");        // % requests com fallbackApplied=true
const httpFailRate = new Rate("http_fail_rate");       // % status != 200
const parseFailRate = new Rate("parse_fail_rate");     // % resposta não parseável JSON

const fallbacks = new Counter("fallback_count");
const successes = new Counter("success_count");

const endToEnd = new Trend("e2e_ms");                  // tempo total (k6)
const appMs = new Trend("app_ms");                     // mesmo que e2e, mas tagueado
const responseSize = new Trend("resp_bytes");          // tamanho da resposta

/**
 * =========================
 * OPTIONS (cenários)
 * =========================
 *
 * - ramping-arrival-rate é mais “profissional” para API: você controla RPS/Throughput.
 * - Se preferir VUs, troque pelo executor "ramping-vus".
 */
export const options = {
  scenarios: {
    load: {
      executor: "ramping-arrival-rate",
      startRate: parseInt(__ENV.START_RPS || "50", 10),
      timeUnit: "1s",
      preAllocatedVUs: parseInt(__ENV.PREALLOC_VUS || "200", 10),
      maxVUs: parseInt(__ENV.MAX_VUS || "2000", 10),
      stages: [
        { duration: __ENV.STAGE1 || "20s", target: parseInt(__ENV.RPS1 || "200", 10) },
        { duration: __ENV.STAGE2 || "20s", target: parseInt(__ENV.RPS2 || "500", 10) },
        { duration: __ENV.STAGE3 || "20s", target: parseInt(__ENV.RPS3 || "1000", 10) },
        { duration: __ENV.STAGE4 || "20s", target: parseInt(__ENV.RPS4 || "1200", 10) },
      ],
      gracefulStop: __ENV.GRACEFUL_STOP || "10s",
    },
  },

  thresholds: {
    // sua API deve responder 200 quase sempre
    http_fail_rate: ["rate<0.01"],

    // fallback não deve passar de X% (ajuste conforme sua política)
    fallback_rate: [`rate<${__ENV.FALLBACK_MAX || "0.20"}`], // default: <20%

    // SLA de latência
    e2e_ms: [`p(95)<${__ENV.P95_MAX || "650"}`, `p(99)<${__ENV.P99_MAX || "1200"}`],
  },
};

/**
 * =========================
 * HELPERS
 * =========================
 */
function pickClass() {
  const total = W_FAST + W_MED + W_SLOW + W_ERR;
  const r = Math.random() * total;

  if (r < W_FAST) return "fast";
  if (r < W_FAST + W_MED) return "med";
  if (r < W_FAST + W_MED + W_SLOW) return "slow";
  return "err";
}

function valueForClass(cls) {
  switch (cls) {
    case "fast": return VALUE_FAST;
    case "med":  return VALUE_MED;
    case "slow": return VALUE_SLOW;
    case "err":  return VALUE_ERR;
    default: return VALUE_FAST;
  }
}

function makeBigData(seed) {
  // Simula payload com ~200 campos
  // strings pequenas para não explodir memória do k6
  const data = {};
  for (let i = 1; i <= BIG_FIELDS; i++) {
    const key = `field${String(i).padStart(3, "0")}`;
    data[key] = `${seed}-${i}-` + "x".repeat(BIG_STRING_SIZE);
  }
  // alguns tipos variados
  data["flagActive"] = (seed % 2 === 0);
  data["amount"] = seed % 10000;
  data["note"] = "payload-big";
  return data;
}

/**
 * =========================
 * TEST
 * =========================
 */
export default function () {
  const cls = pickClass();
  const value = valueForClass(cls);

  const url = `${BASE_URL}${ENDPOINT}`;

  const seed = (__VU * 100000) + __ITER;
  const payload = {
    value,
    customerId: `C-${PROFILE}-${__VU}`,
    data: makeBigData(seed),
  };

  const params = {
    headers: { "Content-Type": "application/json" },
    timeout: K6_REQ_TIMEOUT,
    tags: {
      profile: PROFILE,
      class: cls,             // fast|med|slow|err
      value: String(value),
    },
  };

  const start = Date.now();
  const res = http.post(url, JSON.stringify(payload), params);
  const elapsed = Date.now() - start;

  endToEnd.add(elapsed, params.tags);
  appMs.add(elapsed, params.tags);

  responseSize.add(res.body ? res.body.length : 0, params.tags);

  const ok200 = res.status === 200;
  httpFailRate.add(!ok200, params.tags);

  // Checagens mínimas para evitar “falso positivo”
  check(res, {
    "status is 200": (r) => r.status === 200,
  });

  // Parse do JSON e medição de fallbackApplied
  try {
    const body = JSON.parse(res.body);

    const fb = body && body.fallbackApplied === true;
    fallbackRate.add(fb, params.tags);
    if (fb) fallbacks.add(1, params.tags);
    else successes.add(1, params.tags);

  } catch (e) {
    // resposta não parseável (pode ser HTML de erro, etc.)
    parseFailRate.add(1, params.tags);
  }

  sleep(THINK_TIME);
}
