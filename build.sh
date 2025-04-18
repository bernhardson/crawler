#!/bin/bash

# Paths
SRC_DIR=src
OUT_DIR=out
JAR_NAME=crawler.jar
MAIN_CLASS=org.bernhardson.Crawler

# Create output directory
mkdir -p "$OUT_DIR"

# Compile Java files
find "$SRC_DIR" -name "*.java" > sources.txt
javac -d "$OUT_DIR" @sources.txt

# Create manifest file
echo "Main-Class: $MAIN_CLASS" > "$OUT_DIR/MANIFEST.MF"

# Package into executable JAR
jar cfm "$JAR_NAME" "$OUT_DIR/MANIFEST.MF" -C "$OUT_DIR" .

# Cleanup
rm sources.txt "$OUT_DIR/MANIFEST.MF"

echo " Build complete: ./$JAR_NAME"
