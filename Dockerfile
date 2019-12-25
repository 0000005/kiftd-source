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
RUN apt update -y && apt-get install openjdk-8-jdk libgomp1 wget sudo git -y

#安装cnocr
RUN pip install cnocr

RUN git clone https://github.com/breezedeus/cnocr && cd cnocr && python3.6 ./setup.py install && pip uninstall -y mxnet

#安装mxnet
RUN wget "http://mirror.bit.edu.cn/apache/incubator/mxnet/1.4.1/apache-mxnet-src-1.4.1-incubating.tar.gz" \
	&& tar -zxvf apache-mxnet-src-1.4.1-incubating.tar.gz 

RUN cd ./apache-mxnet-src-1.4.1-incubating \
    && cp make/config.mk . \
	&& echo "USE_BLAS=openblas" >> config.mk \
	&& echo "USE_OPENCV=openblas" >> config.mk \
	&& echo "USE_OPENCV=1" >> config.mk 
	
RUN apt update -y \
	&& sudo apt-get install -y \
    apt-transport-https \
    build-essential \
    ca-certificates \
    cmake \
    curl \
    libatlas-base-dev \
    libcurl4-openssl-dev \
    libjemalloc-dev \
    liblapack-dev \
    libopenblas-dev \
    libopencv-dev \
    libzmq3-dev \
    ninja-build \
    software-properties-common \
    unzip \
    virtualenv \
    && cd ./apache-mxnet-src-1.4.1-incubating \
    && make -j4

RUN cd ./apache-mxnet-src-1.4.1-incubating/python \
	&& sudo python3.6 setup.py install

COPY . /usr/local/kiftd

WORKDIR /usr/local/kiftd

# 配置cnocr
RUN mkdir -p ~/.cnocr && mv /usr/local/kiftd/cnocr/models ~/.cnocr

#安裝darknet-ocr
RUN cd darknet-ocr \
    && pip install -r requirements.txt \
    && cp -rf ./models/ ../
    
EXPOSE 8081

CMD java -jar kiftd-1.0.25-RELEASE.jar -start

