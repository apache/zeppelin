FROM continuumio/miniconda3:24.11.1-0

# Build argument for environment file path
ARG ENV_FILE=testing/env_python_3.9_with_R.yml

# Metadata labels
LABEL org.opencontainers.image.source=https://github.com/apache/zeppelin
LABEL org.opencontainers.image.description="Zeppelin test environment with Python 3.9 and R"

# Install mamba for faster and more reliable dependency resolution
# Configure channels to match GitHub Actions setup-miniconda settings
RUN conda install -n base -c conda-forge mamba -y && \
    conda config --add channels conda-forge && \
    conda config --add channels defaults && \
    conda config --set channel_priority strict

# Copy environment file
COPY ${ENV_FILE} /tmp/environment.yml

# Create conda environment using mamba (avoids libsolv solver crashes)
RUN mamba env create -f /tmp/environment.yml && \
    conda clean -afy && \
    rm /tmp/environment.yml

# Set environment variables
ENV PATH=/opt/conda/envs/python_3_with_R/bin:$PATH \
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
    CONDA_DEFAULT_ENV=python_3_with_R

# Install R IRkernel
RUN /opt/conda/envs/python_3_with_R/bin/R -e "IRkernel::installspec(user = TRUE)"

# Install Java 11, Node.js 16, and other dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        gnupg \
        git \
        curl \
        ca-certificates && \
    # Install Adoptium Temurin JDK 11
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" > /etc/apt/sources.list.d/adoptium.list && \
    # Install Node.js 16.x (required for zeppelin-web frontend build)
    curl -fsSL https://deb.nodesource.com/setup_16.x | bash - && \
    apt-get update && \
    apt-get install -y --no-install-recommends temurin-11-jdk nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/temurin-11-jdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /workspace

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD python --version && conda --version

CMD ["/bin/bash"]
