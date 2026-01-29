import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  scenarios: {
    ramp: {
      executor: "ramping-vus",
      startVUs: 10,
      stages: [
        { duration: "20s", target: 200 },
        { duration: "20s", target: 500 },
        { duration: "20s", target: 1000 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<650"],
  },
};

export default function () {
  const url = "http://localhost:8080/v1/gateway/process";
  const payload = JSON.stringify({
    value: 350,
    customerId: "C1",
    data: {
      field001: "abc",
      field002: 123,
      field003: true,
      field004: "lorem ipsum"
    }
  });

  const res = http.post(url, payload, { headers: { "Content-Type": "application/json" } });
  check(res, { "status 200": (r) => r.status === 200 });
  sleep(0.05);
}
