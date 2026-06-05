import { computed, ref } from 'vue'

const LOCALE_STORAGE_KEY = 'smite.locale'

const messages = {
  ko: {
    'login.email': '이메일',
    'login.password': '비밀번호',
    'login.forgotPassword': '비밀번호 찾기',
    'login.signUp': '회원가입',
    'login.submit': '로그인',
    'login.submitting': '로그인 중',
    'login.bridgeWith': '다른 계정으로 계속',
    'login.continueGoogle': 'Google로 계속',
    'login.about': '게임 소개',
    'login.required': '이메일과 비밀번호를 입력해 주세요.',
    'login.failed': '로그인에 실패했습니다. 다시 시도해 주세요.',
    'match.records': '전적 보기',
    'match.logout': '로그아웃',
    'match.ranking': '랭킹',
    'match.myRank': '내 순위',
    'match.top': '상위',
    'match.seasonBest': '시즌 최고',
    'match.currentRank': '현재 랭크',
    'match.toNextRank': 'Bronze III까지 152 LP',
    'match.start': '매칭 시작',
    'match.connecting': '연결 중',
    'match.joining': '진입 중',
    'match.cancel': '매칭 취소',
    'match.leaving': '취소 중',
    'match.practice': '연습 모드',
    'match.custom': '사용자 지정',
    'match.errorTitle': '매칭 오류',
    'match.errorConfirm': '확인',
    'match.streamFailed': '매칭 연결에 실패했습니다. 다시 시도해 주세요.',
    'match.foundTitle': '매칭 성사',
    'match.foundSubtitle': '상대를 찾았습니다',
    'match.responseTime': '응답 대기 시간',
    'match.accept': '수락',
    'match.decline': '거절',
    'match.accepting': '수락 중...',
    'match.declining': '거절 중...',
    'match.accepted': '수락 완료',
    'match.declined': '거절 완료',
    'match.waitingForOpponent': '상대 응답 대기',
    'match.waitingForResult': '결과 대기',
    'match.processingResponse': '응답 처리 중...',
    'match.responseFailed': '매칭 응답에 실패했습니다. 다시 시도해 주세요.',
    'match.resultFailed': '매칭 결과를 처리하지 못했습니다. 다시 시도해 주세요.',
    'match.loading': '로딩중...',
    'gameWaiting.title': '게임 준비 중',
    'gameWaiting.status': '전투 데이터를 동기화하는 중',
    'gameWaiting.myInfo': '내 정보',
    'gameWaiting.opponentInfo': '상대 정보',
    'gameWaiting.tier': '티어',
    'gameWaiting.matchData': '매칭 정보',
    'gameWaiting.gameSetup': '게임 준비',
    'gameWaiting.video': '전장 데이터',
    'gameWaiting.socket': '연결 정보',
    'gameWaiting.unknownOpponent': '상대 정보 대기',
    'gameWaiting.payloadMissing': '게임 준비 정보를 찾을 수 없습니다. 매칭 화면으로 돌아갑니다.',
    'gameWaiting.ready': '수신 완료',
    'gameWaiting.pending': '수신 대기',
    'gameWaiting.socketPending': '연결 대기',
    'gameWaiting.socketConnecting': '대기방 연결 중',
    'gameWaiting.socketConnectingDetail': '게임 대기방에 접속하는 중입니다.',
    'gameWaiting.socketConnected': '대기방 연결됨',
    'gameWaiting.socketConnectedDetail': '전장 데이터를 확인할 준비가 되었습니다.',
    'gameWaiting.videoPreloading': '전장 데이터 확인 중',
    'gameWaiting.videoPreloadingDetail':
      '게임 시작 전에 필요한 MP4 데이터를 미리 불러오는 중입니다.',
    'gameWaiting.readySent': '준비 신호 전송됨',
    'gameWaiting.readySentDetail': '내 준비 신호를 보냈고 상대 준비를 기다리는 중입니다.',
    'gameWaiting.videoPreloadFailed': '전장 데이터를 불러오지 못했습니다.',
    'gameWaiting.waitingOpponentReady': '상대 준비 대기',
    'gameWaiting.waitingOpponentReadyDetail': '상대방의 준비 신호를 기다리는 중입니다.',
    'gameWaiting.bothReady': '양쪽 준비 완료',
    'gameWaiting.bothReadyDetail': '양쪽 준비가 끝났고 연결 품질 확인을 기다리는 중입니다.',
    'gameWaiting.rttMeasuring': '연결 품질 확인 중',
    'gameWaiting.rttMeasuringDetail': '서버와 왕복 지연 시간을 확인하는 중입니다.',
    'gameWaiting.websocketFailed': '게임 대기 연결 실패',
    'gameWaiting.websocketClosed': '게임 대기 연결이 종료되었습니다.',
    'gameWaiting.watchdogTimeout': '게임 대기 시간이 초과되었습니다.',
    'gameWaiting.returningToMatch': '매칭 화면으로 돌아갑니다.',
    'gameWaiting.playerLeft': '상대 연결 이탈',
    'gameWaiting.socketDetail': '게임 시작 전 연결 상태를 확인하는 중',
  },
  en: {
    'login.email': 'Email',
    'login.password': 'Password',
    'login.forgotPassword': 'Forgot Password?',
    'login.signUp': 'Sign Up',
    'login.submit': 'Login',
    'login.submitting': 'Logging in',
    'login.bridgeWith': 'Or bridge with',
    'login.continueGoogle': 'Continue with Google',
    'login.about': 'About This Game',
    'login.required': 'Email and password are required.',
    'login.failed': 'Failed to login. Please try again.',
    'match.records': 'Records',
    'match.logout': 'Logout',
    'match.ranking': 'Ranking',
    'match.myRank': 'My Rank',
    'match.top': 'Top',
    'match.seasonBest': 'Season Best',
    'match.currentRank': 'Current Rank',
    'match.toNextRank': '152 LP to Bronze III',
    'match.start': 'Start Matching',
    'match.connecting': 'Connecting',
    'match.joining': 'Joining',
    'match.cancel': 'Cancel Matching',
    'match.leaving': 'Canceling',
    'match.practice': 'Practice Mode',
    'match.custom': 'Custom Game',
    'match.errorTitle': 'Match Error',
    'match.errorConfirm': 'OK',
    'match.streamFailed': 'Failed to connect matching. Please try again.',
    'match.foundTitle': 'Match Found',
    'match.foundSubtitle': 'Opponent Found',
    'match.responseTime': 'Response Time',
    'match.accept': 'Accept',
    'match.decline': 'Decline',
    'match.accepting': 'Accepting...',
    'match.declining': 'Declining...',
    'match.accepted': 'Accepted',
    'match.declined': 'Declined',
    'match.waitingForOpponent': 'Waiting for opponent',
    'match.waitingForResult': 'Waiting for result',
    'match.processingResponse': 'Processing response...',
    'match.responseFailed': 'Failed to send match response. Please try again.',
    'match.resultFailed': 'Failed to process match result. Please try again.',
    'match.loading': 'loading...',
    'gameWaiting.title': 'Preparing Game',
    'gameWaiting.status': 'Synchronizing combat data',
    'gameWaiting.myInfo': 'My Info',
    'gameWaiting.opponentInfo': 'Opponent Info',
    'gameWaiting.tier': 'Tier',
    'gameWaiting.matchData': 'Match Data',
    'gameWaiting.gameSetup': 'Game Setup',
    'gameWaiting.video': 'Battle Data',
    'gameWaiting.socket': 'Connection Info',
    'gameWaiting.unknownOpponent': 'Waiting for opponent',
    'gameWaiting.payloadMissing': 'Game preparation data is missing. Returning to matchmaking.',
    'gameWaiting.ready': 'Received',
    'gameWaiting.pending': 'Waiting',
    'gameWaiting.socketPending': 'Connection pending',
    'gameWaiting.socketConnecting': 'Connecting room',
    'gameWaiting.socketConnectingDetail': 'Connecting to the game waiting room.',
    'gameWaiting.socketConnected': 'Room connected',
    'gameWaiting.socketConnectedDetail': 'Ready to check battle data.',
    'gameWaiting.videoPreloading': 'Checking battle data',
    'gameWaiting.videoPreloadingDetail': 'Preloading the required MP4 data before game start.',
    'gameWaiting.readySent': 'Ready signal sent',
    'gameWaiting.readySentDetail': 'Your ready signal was sent. Waiting for the opponent.',
    'gameWaiting.videoPreloadFailed': 'Failed to load battle data.',
    'gameWaiting.waitingOpponentReady': 'Waiting for opponent',
    'gameWaiting.waitingOpponentReadyDetail': 'Waiting for the opponent ready signal.',
    'gameWaiting.bothReady': 'Both players ready',
    'gameWaiting.bothReadyDetail': 'Both players are ready. Waiting for connection quality check.',
    'gameWaiting.rttMeasuring': 'Checking connection quality',
    'gameWaiting.rttMeasuringDetail': 'Measuring round-trip latency with the server.',
    'gameWaiting.websocketFailed': 'Game waiting connection failed',
    'gameWaiting.websocketClosed': 'Game waiting connection closed.',
    'gameWaiting.watchdogTimeout': 'Game waiting timed out.',
    'gameWaiting.returningToMatch': 'Returning to matchmaking.',
    'gameWaiting.playerLeft': 'Opponent disconnected',
    'gameWaiting.socketDetail': 'Checking the connection before game start',
  },
} as const

type Locale = keyof typeof messages
type MessageKey = keyof (typeof messages)['ko']

const locale = ref<Locale>(readStoredLocale())

export function useLocale() {
  const nextLocaleLabel = computed(() => (locale.value === 'ko' ? 'EN' : 'KO'))

  function t(key: MessageKey): string {
    return messages[locale.value][key]
  }

  function setLocale(nextLocale: Locale) {
    locale.value = nextLocale
    getStorage()?.setItem(LOCALE_STORAGE_KEY, nextLocale)
  }

  function toggleLocale() {
    setLocale(locale.value === 'ko' ? 'en' : 'ko')
  }

  return {
    locale,
    nextLocaleLabel,
    setLocale,
    toggleLocale,
    t,
  }
}

function readStoredLocale(): Locale {
  const storedLocale = getStorage()?.getItem(LOCALE_STORAGE_KEY)

  return storedLocale === 'en' || storedLocale === 'ko' ? storedLocale : 'ko'
}

function getStorage(): Storage | null {
  return typeof window === 'undefined' ? null : window.localStorage
}
