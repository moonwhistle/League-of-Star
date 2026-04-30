import exec from 'k6/execution';
import {
  joinQueue,
  joinQueueThresholds,
  prepareUsers,
  totalUsers,
  vus,
} from './lib/join-queue-common.js';

export const options = {
  scenarios: {
    join_queue_burst: {
      executor: 'shared-iterations',
      vus,
      iterations: totalUsers,
      maxDuration: __ENV.MAX_DURATION || '5m',
    },
  },
  thresholds: joinQueueThresholds(),
};

export function setup() {
  return prepareUsers(totalUsers);
}

export default function (data) {
  joinQueue(exec.scenario.iterationInTest, data);
}
