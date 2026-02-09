# Expects target/AltoEditor-*.jar from local build. Run: mvn package  then  podman build -f Containerfile -t alto-editor:1.6.0 .
FROM eclipse-temurin:21-jre-alpine

# Install Python for Pero OCR engine script (symlink python -> python3 for engine entry "python")
RUN apk add --no-cache python3 py3-pip && ln -sf /usr/bin/python3 /usr/local/bin/python

WORKDIR /opt/alto-editor

# Copy pre-built JAR from host target/ (build with mvn package first)
COPY target/AltoEditor-*.jar ./lib/altoEditor.jar

# Python dependencies for pero-ocr.py (Pillow, requests, requests-toolbelt)
COPY requirements.txt /tmp/requirements.txt
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt --break-system-packages && rm /tmp/requirements.txt

# Pero OCR script: application.yml expects entry e.g. /usr/local/bin/pero.py
COPY src/main/resources/cz/inovatika/altoEditor/pero/pero-ocr.py /usr/local/bin/pero.py

# Directories for app data
RUN mkdir -p log /tmp/altoEditorStore /tmp/hibernate-search-index

# Expose HTTP port (Spring Boot default 8080)
EXPOSE 8080

# Run the application (port 8080 is Spring Boot default; override with SERVER_PORT if needed)
CMD ["java", "-jar", "/opt/alto-editor/lib/altoEditor.jar"]
