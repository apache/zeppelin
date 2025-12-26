#!/bin/bash

# Script to rebuild and redeploy a Zeppelin interpreter
# Usage: ./update_interpreter.sh <interpreter-name>

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if interpreter name is provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: Interpreter name is required${NC}"
    echo "Usage: $0 <interpreter-name>"
    echo "Example: $0 oracle"
    exit 1
fi

INTERPRETER_NAME=$1
ZEPPELIN_VERSION="0.13.0-SNAPSHOT"

# Find Zeppelin root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ZEPPELIN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Script location: $SCRIPT_DIR"
echo "Zeppelin root: $ZEPPELIN_ROOT"

# Change to Zeppelin root directory
cd "$ZEPPELIN_ROOT"

# Verify interpreter directory exists
if [ ! -d "$INTERPRETER_NAME" ]; then
    echo -e "${RED}Error: Interpreter directory '$INTERPRETER_NAME' not found in $ZEPPELIN_ROOT${NC}"
    exit 1
fi

echo -e "${GREEN}Starting update process for interpreter: ${INTERPRETER_NAME}${NC}"
echo "================================================"

# Create log file in logs directory
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOGS_DIR="${ZEPPELIN_ROOT}/logs"
mkdir -p "$LOGS_DIR"
LOG_FILE="${LOGS_DIR}/update_interpreter_${INTERPRETER_NAME}_${TIMESTAMP}.log"
echo "Logging to: $LOG_FILE"
echo ""

exec > >(tee >(sed 's/\x1B\[[0-9;]*[JKmsu]//g' >> "$LOG_FILE")) 2>&1

# Step 1: Build the interpreter
echo -e "\n${YELLOW}Step 1: Building interpreter...${NC}"
mvn clean package -DskipTests -pl "$INTERPRETER_NAME" -am

# Step 2: Copy the JAR file
echo -e "\n${YELLOW}Step 2: Copying JAR file...${NC}"
JAR_SOURCE="${INTERPRETER_NAME}/target/zeppelin-${INTERPRETER_NAME}-${ZEPPELIN_VERSION}.jar"
JAR_DEST="interpreter/${INTERPRETER_NAME}/"

if [ ! -f "$JAR_SOURCE" ]; then
    echo -e "${RED}Error: JAR file not found at ${JAR_SOURCE}${NC}"
    exit 1
fi

mkdir -p "$JAR_DEST"
cp -f "$JAR_SOURCE" "$JAR_DEST"
echo -e "${GREEN}✓ JAR copied successfully${NC}"

# Step 3: Copy the JSON file (if it exists)
echo -e "\n${YELLOW}Step 3: Copying interpreter-setting.json...${NC}"
JSON_SOURCE="${INTERPRETER_NAME}/src/main/resources/interpreter-setting.json"
JSON_DEST="interpreter/${INTERPRETER_NAME}/"

if [ -f "$JSON_SOURCE" ]; then
    cp -f "$JSON_SOURCE" "$JSON_DEST"
    echo -e "${GREEN}✓ JSON copied successfully${NC}"
else
    echo -e "${YELLOW}x No interpreter-setting.json found (skipping)${NC}"
fi

# Check if Zeppelin server is built
echo -e "\n${YELLOW}Checking if Zeppelin server is built...${NC}"
if ! ls zeppelin-server/target/zeppelin-server-*.jar 1> /dev/null 2>&1; then
    echo -e "${YELLOW}x Zeppelin server JAR not found. Building Zeppelin server...${NC}"
    echo ""

    # Build Zeppelin server
    ./mvnw clean package -DskipTests

    # Check if build was successful
    if ! ls zeppelin-server/target/zeppelin-server-*.jar 1> /dev/null 2>&1; then
        echo -e "${RED}✗ Failed to build Zeppelin server!${NC}"
        echo "Please check the build output above for errors."
        exit 1
    fi

    echo -e "${GREEN}✓ Zeppelin server built successfully${NC}"
else
    echo -e "${GREEN}✓ Zeppelin server is built${NC}"
fi

# Step 4: Stop Zeppelin
echo -e "\n${YELLOW}Step 4: Stopping Zeppelin...${NC}"
./bin/zeppelin-daemon.sh stop
sleep 3
echo -e "${GREEN}✓ Zeppelin stopped${NC}"

# Step 5: Clean interpreter configuration
echo -e "\n${YELLOW}Step 5: Cleaning interpreter configuration...${NC}"
if [ -f "conf/interpreter.json" ]; then
    rm -rf conf/interpreter.json
    echo -e "${GREEN}✓ interpreter.json removed${NC}"
else
    echo -e "${YELLOW}x interpreter.json not found (already clean)${NC}"
fi

# Step 6: Clean local repository
echo -e "\n${YELLOW}Step 6: Cleaning local repository...${NC}"
if [ -d "local-repo" ]; then
    rm -rf local-repo/*
    echo -e "${GREEN}✓ local-repo cleaned${NC}"
else
    echo -e "${YELLOW}x local-repo not found (skipping)${NC}"
fi

# Step 7: Start Zeppelin
echo -e "\n${YELLOW}Step 7: Starting Zeppelin...${NC}"
./bin/zeppelin-daemon.sh start

# Wait a bit and check if process is running
sleep 5

# Check if Zeppelin is running
ZEPPELIN_PID=$(cat run/zeppelin-*.pid 2>/dev/null || echo "")

if [ -n "$ZEPPELIN_PID" ] && ps -p "$ZEPPELIN_PID" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Zeppelin started successfully (PID: $ZEPPELIN_PID)${NC}"

    echo -e "\n${GREEN}================================================${NC}"
    echo -e "${GREEN}✓ Interpreter '${INTERPRETER_NAME}' updated successfully!${NC}"
    echo -e "${GREEN}================================================${NC}"
    echo ""
    echo "You can now access Zeppelin and use the updated interpreter."
else
    echo -e "${RED}✗ Zeppelin process died on startup${NC}"
    echo ""
    echo -e "${BLUE}Last 50 lines from zeppelin.log:${NC}"
    echo "================================================"
    tail -50 logs/zeppelin-*.log 2>/dev/null || echo "No log file found"
    echo "================================================"
    echo ""
    echo -e "${YELLOW}To view full logs, run:${NC}"
    echo "  tail -f logs/zeppelin-*.log"
    echo ""
    echo -e "${YELLOW}To check for errors:${NC}"
    echo "  grep -i error logs/zeppelin-*.log"
    exit 1
fi