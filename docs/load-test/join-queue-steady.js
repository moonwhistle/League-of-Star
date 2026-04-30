import exec from 'k6/execution';
import {
  duration,
  durationToSeconds,
  joinQueue,
  joinQueueThresholds,
  prepareUsers,
  totalUsers,
  vus,
} from './lib/join-queue-common.js';

const ratePerMinute = __ENV.RATE_PER_MINUTE ? Number(__ENV.RATE_PER_MINUTE) : null;
const requestRate = ratePerMinute || totalUsers;
const requestTimeUnit = ratePerMinute ? '1m' : duration;
const plannedIterations = ratePerMinute
  ? Math.ceil((ratePerMinute / 60) * durationToSeconds(duration))
  : totalUsers;

export const options = {
  scenarios: {
    join_queue_steady: {
      executor: 'constant-arrival-rate',
      rate: requestRate,
      timeUnit: requestTimeUnit,
      duration,
      preAllocatedVUs: vus,
      maxVUs: Number(__ENV.MAX_VUS || vus * 2),
    },
  },
  thresholds: joinQueueThresholds(),
};

export function setup() {
  return prepareUsers(plannedIterations);
}

export default function (data) {
  joinQueue(exec.scenario.iterationInTest, data);
}
