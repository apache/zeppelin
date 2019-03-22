FROM python:3.6.5-slim-jessie

WORKDIR /usr/src/app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

CMD [ "python", "-u", "python/ipython_server.py", "50053" ]