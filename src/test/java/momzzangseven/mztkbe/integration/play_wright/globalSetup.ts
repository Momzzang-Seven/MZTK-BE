/**
 * Playwright Global Setup
 *
 * 모든 테스트 실행 전에 백엔드 서버가 기동되어 있는지 확인합니다.
 * 서버가 응답하지 않으면 즉시 에러를 내고 테스트 전체를 중단합니다.
 */

import * as dotenv from "dotenv";
import * as path from "path";

dotenv.config({ path: path.resolve(__dirname, ".env") });

export default async function globalSetup() {
  const backendUrl = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";
  const healthUrl = `${backendUrl}/actuator/health`;

  console.log(`\n[globalSetup] 백엔드 서버 헬스체크: ${healthUrl}`);

  try {
    const res = await fetch(healthUrl, { signal: AbortSignal.timeout(5_000) });
    if (!res.ok) {
      throw new Error(`헬스체크 HTTP ${res.status}`);
    }
    const body = await res.json() as Record<string, unknown>;
    console.log(`[globalSetup] ✅ 서버 정상 기동 확인 (status: ${body["status"]})\n`);
  } catch (err) {
    const message =
      err instanceof Error && err.message.includes("fetch")
        ? "연결 거부됨 — 서버가 실행 중이지 않습니다"
        : String(err);

    throw new Error(
      `\n\n` +
      `╔══════════════════════════════════════════════════════════╗\n` +
      `║  백엔드 서버가 실행되지 않아 테스트를 시작할 수 없습니다  ║\n` +
      `╠══════════════════════════════════════════════════════════╣\n` +
      `║  원인: ${message.padEnd(50)}║\n` +
      `║                                                          ║\n` +
      `║  해결: MZTK-BE 프로젝트 루트에서 아래 명령어를 먼저 실행  ║\n` +
      `║  $ ./gradlew bootRun                                     ║\n` +
      `║  (또는 IntelliJ에서 MztkBeApplication 실행)              ║\n` +
      `╚══════════════════════════════════════════════════════════╝\n`
    );
  }
}
