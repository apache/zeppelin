FROM continuumio/miniconda3:24.1.2-0

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

# Install R IRkernel
RUN /opt/conda/envs/python_3_with_R/bin/R -e "IRkernel::installspec(user = TRUE)"

# Install Java 11 for Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-11-jdk \
        git \
        curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV PATH=/opt/conda/envs/python_3_with_R/bin:$PATH \
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
    CONDA_DEFAULT_ENV=python_3_with_R

WORKDIR /workspace

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
  CMD python --version && conda --version

CMD ["/bin/bash"]
