FROM eclipse-temurin:21-jre-alpine

# Install Python for Pero VUT engine script
RUN apk add --no-cache python3 py3-pip && ln -sf /usr/bin/python3 /usr/local/bin/python

WORKDIR /opt/alto-editor

# Copy pre-built JAR from host target/ (build with mvn package first)
COPY target/AltoEditor-*.jar ./lib/altoEditor.jar

# Python dependencies for pero-vut.py
COPY requirements.txt /tmp/requirements.txt
RUN pip3 install --no-cache-dir -r /tmp/requirements.txt --break-system-packages && rm /tmp/requirements.txt

# Set environment variable for application home
ENV altoeditor.home=/opt/alto-editor

# Copy default application.yml (can be overridden by mounting a custom one)
COPY src/main/resources/cz/inovatika/altoEditor/application.yml /opt/alto-editor/application.yml

# Pero VUT script: application.yml expected entry /usr/local/bin/pero-vut.py
COPY src/main/resources/cz/inovatika/altoEditor/pero-vut.py /usr/local/bin/pero-vut.py

# Expose HTTP port (Spring Boot default 8080)
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "/opt/alto-editor/lib/altoEditor.jar"]
