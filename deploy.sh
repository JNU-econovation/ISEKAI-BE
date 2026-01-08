#!/bin/bash
set -e

APP_NAME="isek-ai"
JAR_PATH="$(pwd)/build/libs"
JAR_FILE=$(ls $JAR_PATH/*.jar | grep -v 'plain' | head -n 1)
LOG_PATH="../log/$APP_NAME"
PID_FILE="/tmp/$APP_NAME.pid"

export SPRING_PROFILES_ACTIVE=prod
export JAVA_HOME=/opt/java/openjdk
export PATH=$JAVA_HOME/bin:$PATH

echo "🔄 Git main 브랜치 pull 중..."
git fetch origin main
git reset --hard origin/main

echo "🔧 ./gradlew 실행 권한 확인 중..."
chmod +x ./gradlew

echo "🧹 이전 빌드 정리 및 새 빌드 시작..."
./gradlew clean build -x test

echo "✅ 빌드 완료: $JAR_FILE"

# 로그 디렉토리 생성
mkdir -p $LOG_PATH

# 기존 프로세스 중지
if [ -f "$PID_FILE" ]; then
    PID=$(cat $PID_FILE)
    if ps -p $PID > /dev/null; then
        echo "🛑 기존 애플리케이션($PID) 종료 중..."
        kill -15 $PID
        sleep 5
    fi
    rm -f $PID_FILE
fi

# 새 프로세스 실행
echo "🚀 새 애플리케이션 실행 중..."
nohup java -jar -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE $JAR_FILE \
    > $LOG_PATH/app.log 2>&1 &

echo $! > $PID_FILE

echo "✅ 배포 완료 (PID: $(cat $PID_FILE))"
echo "📜 로그 파일: $LOG_PATH/app.log"
