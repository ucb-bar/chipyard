### Note: This DockerFile is adapted from https://github.com/CircleCI-Public/example-images/openjdk

FROM openjdk:11.0.1-jdk-sid

# man directory is missing in some base images
# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199
RUN apt-get update \
    && mkdir -p /usr/share/man/man1 \
    && apt-get install -y \
               bzip2 \
               ca-certificates \
               curl \
               git \
               gnupg \
               gzip \
               libfl2 \
               libfl-dev \
               locales \
               mercurial \
               netcat \
               net-tools \
               openssh-client \
               parallel \
               sudo \
               tar \
               unzip \
               wget \
               xvfb \
               xxd \
               zip \
               ccache \
               libgoogle-perftools-dev \
               numactl \
               zlib1g

# Set timezone to UTC by default
RUN ln -sf /usr/share/zoneinfo/Etc/UTC /etc/localtime

# Use unicode
RUN locale-gen C.UTF-8 || true
ENV LANG=C.UTF-8

# install jq
RUN JQ_URL="https://circle-downloads.s3.amazonaws.com/circleci-images/cache/linux-amd64/jq-latest" \
    && curl --silent --show-error --location --fail --retry 3 --output /usr/bin/jq $JQ_URL \
    && chmod +x /usr/bin/jq \
    && jq --version

# Install Docker

# Docker.com returns the URL of the latest binary when you hit a directory listing
# We curl this URL and `grep` the version out.
# The output looks like this:

#>    # To install, run the following commands as root:
#>    curl -fsSLO https://download.docker.com/linux/static/stable/x86_64/docker-17.05.0-ce.tgz && tar --strip-components=1 -xvzf docker-17.05.0-ce.tgz -C /usr/local/bin
#>
#>    # Then start docker in daemon mode:
#>    /usr/local/bin/dockerd

RUN set -ex \
    && export DOCKER_VERSION=$(curl --silent --fail --retry 3 https://download.docker.com/linux/static/stable/x86_64/ | grep -o -e 'docker-[.0-9]*-ce\.tgz' | sort -r | head -n 1) \
    && DOCKER_URL="https://download.docker.com/linux/static/stable/x86_64/${DOCKER_VERSION}" \
    && echo Docker URL: $DOCKER_URL \
    && curl --silent --show-error --location --fail --retry 3 --output /tmp/docker.tgz "${DOCKER_URL}" \
    && ls -lha /tmp/docker.tgz \
    && tar -xz -C /tmp -f /tmp/docker.tgz \
    && mv /tmp/docker/* /usr/bin \
    && rm -rf /tmp/docker /tmp/docker.tgz \
    && which docker \
    && (docker version || true)

# docker compose
RUN COMPOSE_URL="https://circle-downloads.s3.amazonaws.com/circleci-images/cache/linux-amd64/docker-compose-latest" \
    && curl --silent --show-error --location --fail --retry 3 --output /usr/bin/docker-compose $COMPOSE_URL \
    && chmod +x /usr/bin/docker-compose \
    && docker-compose version

# install dockerize
RUN DOCKERIZE_URL="https://circle-downloads.s3.amazonaws.com/circleci-images/cache/linux-amd64/dockerize-latest.tar.gz" \
    && curl --silent --show-error --location --fail --retry 3 --output /tmp/dockerize-linux-amd64.tar.gz $DOCKERIZE_URL \
    && tar -C /usr/local/bin -xzvf /tmp/dockerize-linux-amd64.tar.gz \
    && rm -rf /tmp/dockerize-linux-amd64.tar.gz \
    && dockerize --version

RUN groupadd --gid 3434 riscvuser \
    && useradd --uid 3434 --gid riscvuser --shell /bin/bash --create-home riscvuser \
    && echo 'riscvuser ALL=NOPASSWD: ALL' >> /etc/sudoers.d/50-riscvuser \
    && echo 'Defaults    env_keep += "DEBIAN_FRONTEND"' >> /etc/sudoers.d/env_keep

# BEGIN IMAGE CUSTOMIZATIONS

# cacerts from OpenJDK 9-slim to workaround http://bugs.java.com/view_bug.do?bug_id=8189357
# AND https://github.com/docker-library/openjdk/issues/145
#
# Created by running:
# docker run --rm openjdk:9-slim cat /etc/ssl/certs/java/cacerts | #   aws s3 cp - s3://circle-downloads/circleci-images/cache/linux-amd64/openjdk-9-slim-cacerts --acl public-read
RUN if java -fullversion 2>&1 | grep -q '"9.'; then curl --silent --show-error --location --fail --retry 3 --output /etc/ssl/certs/java/cacerts https://circle-downloads.s3.amazonaws.com/circleci-images/cache/linux-amd64/openjdk-9-slim-cacerts; fi

# Install Maven Version: 3.6.3
RUN curl --silent --show-error --location --fail --retry 3 --output /tmp/apache-maven.tar.gz https://www.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz \
    && tar xf /tmp/apache-maven.tar.gz -C /opt/ \
    && rm /tmp/apache-maven.tar.gz \
    && ln -s /opt/apache-maven-* /opt/apache-maven \
    && /opt/apache-maven/bin/mvn -version

# Install Ant Version: 1.10.5
RUN curl --silent --show-error --location --fail --retry 3 --output /tmp/apache-ant.tar.gz https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.5-bin.tar.gz \
    && tar xf /tmp/apache-ant.tar.gz -C /opt/ \
    && ln -s /opt/apache-ant-* /opt/apache-ant \
    && rm -rf /tmp/apache-ant.tar.gz \
    && /opt/apache-ant/bin/ant -version

ENV ANT_HOME=/opt/apache-ant

# Install Gradle Version: 5.0
RUN curl --silent --show-error --location --fail --retry 3 --output /tmp/gradle.zip https://services.gradle.org/distributions/gradle-5.0-bin.zip \
    && unzip -d /opt /tmp/gradle.zip \
    && rm /tmp/gradle.zip \
    && ln -s /opt/gradle-* /opt/gradle \
    && /opt/gradle/bin/gradle -version

# Install sbt from https://circle-downloads.s3.amazonaws.com/circleci-images/cache/linux-amd64/sbt-latest.tgz
RUN curl --silent --show-error --location --fail --retry 3 --output /tmp/sbt.tgz https://circle-downloads.s3.amazonaws.com/circleci-images/cache/linux-amd64/sbt-latest.tgz \
    && tar -xzf /tmp/sbt.tgz -C /opt/ \
    && rm /tmp/sbt.tgz \
    && /opt/sbt/bin/sbt sbtVersion

# Install openjfx
RUN apt-get update
RUN apt-get install -y --no-install-recommends openjfx

# Add build-essential
RUN apt-get install -y build-essential

# Add RISCV toolchain necessary dependencies
RUN apt-get update
RUN apt-get install -y \
            autoconf \
            automake \
            autotools-dev \
            babeltrace \
            bc \
            curl \
            device-tree-compiler \
            expat \
            flex \
            gawk \
            gperf \
            g++ \
            libexpat-dev \
            libgmp-dev \
            libmpc-dev \
            libmpfr-dev \
            libtool \
            libusb-1.0-0-dev \
            make \
            patchutils \
            pkg-config \
            python \
            python-pexpect-doc \
            python3 \
            texinfo \
            zlib1g-dev \
            rsync

# Use specific bison version to bypass Verilator 4.034 issues
# TODO: When Verilator is bumped, use apt to get newest bison
RUN wget https://ftp.gnu.org/gnu/bison/bison-3.5.4.tar.gz \
    && tar -xvf bison-3.5.4.tar.gz \
    && cd bison-3.5.4 \
    && ./configure && make && make install

# Check bison version is 3.5.4
RUN bison --version

# Add minimal QEMU dependencies
RUN apt-get install -y \
            libfdt-dev \
            libglib2.0-dev \
            libpixman-1-dev

# Install verilator
RUN git clone http://git.veripool.org/git/verilator \
    && cd verilator \
    && git pull \
    && git checkout v4.034 \
    && autoconf && ./configure && make && make install

# Update PATH for Java tools
ENV PATH="/opt/sbt/bin:/opt/apache-maven/bin:/opt/apache-ant/bin:/opt/gradle/bin:$PATH"

# Add HOME environment variable
ENV HOME="/home/riscvuser"

# Update PATH for RISCV toolchain (note: hardcoded for CircleCI)
ENV RISCV="$HOME/riscv-tools-install"
ENV LD_LIBRARY_PATH="$RISCV/lib"
ENV PATH="$RISCV/bin:$PATH"

WORKDIR $HOME
USER riscvuser

# smoke test with path
RUN mvn -version \
    && ant -version \
    && gradle -version \
    && sbt sbtVersion \
    && verilator --version

# remove extra folders
RUN rm -rf project/

# END IMAGE CUSTOMIZATIONS

CMD ["/bin/sh"]
