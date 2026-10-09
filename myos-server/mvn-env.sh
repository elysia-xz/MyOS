#!/usr/bin/env bash
# MyOS 后端构建环境（本机专用）
# 用法：  source mvn-env.sh  然后照常用 mvn / java

export JAVA_HOME="C:\Users\Lenovo\.workbuddy-ai\binaries\jdk\jdk-25.0.4.1+1"
export MVN_HOME="/c/Users/Lenovo/.workbuddy-ai/binaries/maven/apache-maven-3.9.16"
export PATH="$MVN_HOME/bin:$PATH"

echo "JAVA_HOME = $JAVA_HOME"
java -version 2>&1 | head -1
mvn -v 2>&1 | head -1
