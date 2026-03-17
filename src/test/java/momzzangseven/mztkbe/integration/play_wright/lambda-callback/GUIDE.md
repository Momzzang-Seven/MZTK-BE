# 실제 Lambda와 상호작용하는 테스트를 하기 위해서는 다음 단계를 거쳐야 합니다. 

1. mztk 서버를 실행한다. + DB도 실행한다. 
2. terminal을 열어 ngrok을 설치한다.
3. ngrok에 회원가입/로그인한다.
4. ```ngrok http 8080```을 실행한다.
5. 화면에 보이는 ```https://{임의의 숫자}.ngrok-free.app```을 복사한다.
6. lambda/functions/s3-trigger-tmp-to-domain/Configuration/Environment variables/SPRING_BE_BASE_URL에 붙여넣는다.
7. npx playwright test lambda-callback/lambda-callback.spec.ts를 실행한다. 