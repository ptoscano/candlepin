FROM quay.io/centos/centos:centos7
LABEL author="Chris Rog <crog@redhat.com>"

ENV LANG en_US.UTF-8

# gai.conf is needed to avoid timeouts when accessing rubygems
# https://help.rubygems.org/discussions/problems/31074-timeout-error
COPY /gai.conf /etc/gai.conf
COPY /base-scripts/dockerlib.sh /root/
COPY /candlepin-base/setup-devel-env.sh /root/
RUN /bin/bash /root/setup-devel-env.sh

# Need a wrapper script to get proper start/stop behaviour with supervisord:
COPY /candlepin-base/setup-supervisord.sh /root/
RUN /bin/bash /root/setup-supervisord.sh

# Script for actually running the tests, could theoretically move to candlepin
# checkout for easier updating.
COPY /base-scripts/setup-db.sh /root/

# This allows the cp-test file in the branch to be used instead of one baked into the docker image.
RUN ln -s /candlepin-dev/docker/cp-test /usr/bin/cp-test

EXPOSE 8443 22

#CMD ["/usr/bin/cp-test", "-t", "-u", "-r"]
CMD ["true"]
