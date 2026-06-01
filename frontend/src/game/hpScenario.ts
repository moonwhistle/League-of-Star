import type { GameStartScenario, HpTimelineStep } from '@/types/game'

export function getHpAtElapsedMs(scenario: GameStartScenario, elapsedMs: number): number {
  const timeline = scenario.hpTimeline

  if (timeline.length === 0) {
    return 0
  }

  const firstStep = timeline[0]
  const lastStep = timeline[timeline.length - 1]

  if (firstStep === undefined || lastStep === undefined) {
    return 0
  }

  if (elapsedMs <= firstStep.timeMs) {
    return firstStep.hp
  }

  if (elapsedMs >= lastStep.timeMs) {
    return lastStep.hp
  }

  let previousStep = firstStep

  for (const nextStep of timeline.slice(1)) {
    if (elapsedMs <= nextStep.timeMs) {
      return interpolateHp(previousStep, nextStep, elapsedMs)
    }

    previousStep = nextStep
  }

  return lastStep.hp
}

function interpolateHp(
  previousStep: HpTimelineStep,
  nextStep: HpTimelineStep,
  elapsedMs: number,
): number {
  const durationMs = nextStep.timeMs - previousStep.timeMs

  if (durationMs <= 0) {
    return nextStep.hp
  }

  const progress = (elapsedMs - previousStep.timeMs) / durationMs

  return Math.round(previousStep.hp + (nextStep.hp - previousStep.hp) * progress)
}
