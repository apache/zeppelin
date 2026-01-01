FROM continuumio/miniconda3:24.11.1-0

# Build argument for environment file path
ARG ENV_FILE=testing/env_python_3.9_with_R.yml

# Build argument for user configuration (GitHub Actions runner uses UID 1001)
ARG USER_ID=1001
ARG GROUP_ID=1001

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

# Install Java 11, Node.js 16, and other dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        wget \
        gnupg \
        git \
        curl \
        ca-certificates \
        sudo && \
    # Install Adoptium Temurin JDK 11
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" > /etc/apt/sources.list.d/adoptium.list && \
    # Install Node.js 16.x (required for zeppelin-web frontend build)
    curl -fsSL https://deb.nodesource.com/setup_16.x | bash - && \
    apt-get update && \
    apt-get install -y --no-install-recommends temurin-11-jdk nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV PATH=/opt/conda/envs/python_3_with_R/bin:$PATH \
    JAVA_HOME=/usr/lib/jvm/temurin-11-jdk-amd64 \
    CONDA_DEFAULT_ENV=python_3_with_R
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create non-root user with same UID/GID as GitHub Actions runner
RUN groupadd -g ${GROUP_ID} zeppelin && \
    useradd -u ${USER_ID} -g ${GROUP_ID} -m -s /bin/bash zeppelin && \
    echo "zeppelin ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Set proper permissions for conda environment
RUN chown -R zeppelin:zeppelin /opt/conda

# Switch to non-root user
USER zeppelin

# Install R IRkernel as non-root user
RUN /opt/conda/envs/python_3_with_R/bin/R -e "IRkernel::installspec(user = TRUE)"

# Create directories for Maven and npm cache
RUN mkdir -p /home/zeppelin/.m2 /home/zeppelin/.npm /home/zeppelin/.cache

WORKDIR /workspace

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD python --version && conda --version

CMD ["/bin/bash"]
