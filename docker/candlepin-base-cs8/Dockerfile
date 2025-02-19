FROM quay.io/centos/centos:stream8
LABEL author="Chris Rog <crog@redhat.com>"

ENV LANG en_US.UTF-8

# Enable systemd.
RUN dnf -y install systemd && dnf clean all

# gai.conf is needed to avoid timeouts when accessing rubygems
# https://help.rubygems.org/discussions/problems/31074-timeout-error
COPY /gai.conf /etc/gai.conf
COPY /base-scripts/dockerlib.sh /root/
COPY /candlepin-base-cs8/setup-devel-env.sh /root/
RUN /bin/bash /root/setup-devel-env.sh

# Script for actually running the tests, could theoretically move to candlepin
# checkout for easier updating.
COPY /base-scripts/setup-db.sh /root/

# This allows the cp-test file in the branch to be used instead of one baked into the docker image.
RUN ln -s /candlepin-dev/docker/cp-test /usr/bin/cp-test

# Centos Streams 8 uses Python3 so we must create alias to not break existing scripts.
RUN echo 'alias python="python3"' >> ~/.bashrc

EXPOSE 8443

VOLUME ["/sys/fs/cgroup", "/tmp", "/run"]
ENTRYPOINT ["/usr/sbin/init"]
