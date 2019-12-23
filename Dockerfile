# 基础镜像
FROM arwineap/docker-ubuntu-python3.6

# 维护者信息
MAINTAINER superywj@foxmail.com

ENV LANG C.UTF-8

ENV TZ=Asia/Shanghai

RUN echo "${TZ}" > /etc/timezone \
    && ln -sf /usr/share/zoneinfo/${TZ} /etc/localtime \
	&& rm -rf /etc/apt/sources.list.d/jonathonf-ubuntu-python-3_6-xenial.list \
    && apt update \
    && apt install -y tzdata \
    && rm -rf /var/lib/apt/lists/*

# 安装JDK
RUN apt update -y && apt-get install openjdk-8-jdk libgomp1 wget sudo -y

RUN pip install cnocr

RUN wget "http://mirror.bit.edu.cn/apache/incubator/mxnet/1.4.1/apache-mxnet-src-1.4.1-incubating.tar.gz" \
	&& tar -zxvf apache-mxnet-src-1.4.1-incubating.tar.gz \
	&& cd ./apache-mxnet-src-1.4.1-incubating/docs/install \
    && ./install_mxnet_ubuntu_python.sh \
	&& cd ../../python \
	&& python3.6 ./setup.py install \
	&& pip install -e .

COPY . /usr/local/kiftd

WORKDIR /usr/local/kiftd

# 配置cnocr
RUN python3.6 /usr/local/kiftd/cnocr/setup.py install && mkdir -p ~/.cnocr && mv /usr/local/kiftd/cnocr/models ~/.cnocr && pip uninstall -y mxnet
    
EXPOSE 8081

CMD java -jar kiftd-1.0.25-RELEASE.jar -start

