FROM eclipse-temurin:21-jre-alpine

# Install Python for Pero VUT engine script
RUN apk add --no-cache python3 py3-pip && ln -sf /usr/bin/python3 /usr/local/bin/python

WORKDIR /opt/alto-editor

# Copy pre-built JAR from host target/ (build with mvn package first)
COPY target/AltoEditor-*.jar ./lib/altoEditor.jar

# Set environment variable for application home
ENV altoeditor.home=/opt/alto-editor

# Copy default application.yml (can be overridden by mounting a custom one)
COPY src/main/resources/cz/inovatika/altoEditor/application.yml /opt/alto-editor/application.yml

# Pero scripts
COPY src/main/resources/cz/inovatika/altoEditor/pero-vut/client.py /usr/local/bin/pero-vut/client.py
COPY src/main/resources/cz/inovatika/altoEditor/pero-distributed/constants.py /usr/local/bin/pero-distributed/constants.py
COPY src/main/resources/cz/inovatika/altoEditor/pero-distributed/convert.py /usr/local/bin/pero-distributed/convert.py
COPY src/main/resources/cz/inovatika/altoEditor/pero-distributed/models.py /usr/local/bin/pero-distributed/models.py
COPY src/main/resources/cz/inovatika/altoEditor/pero-distributed/pero_client.py /usr/local/bin/pero-distributed/pero_client.py
COPY src/main/resources/cz/inovatika/altoEditor/pero-distributed/client.py /usr/local/bin/pero-distributed/client.py

# Python dependencies for Pero VUT script
COPY src/main/resources/cz/inovatika/altoEditor/pero-vut/requirements.txt /tmp/requirements.pero-vut.txt
RUN pip3 install --no-cache-dir -r /tmp/requirements.pero-vut.txt --break-system-packages && rm /tmp/requirements.pero-vut.txt

# Python dependencies for Pero Distributed script
COPY src/main/resources/cz/inovatika/altoEditor/pero-distributed/requirements.txt /tmp/requirements.pero-distributed.txt
RUN pip3 install --no-cache-dir -r /tmp/requirements.pero-distributed.txt --break-system-packages && rm /tmp/requirements.pero-distributed.txt

# Expose HTTP port (Spring Boot default 8080)
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "/opt/alto-editor/lib/altoEditor.jar"]
