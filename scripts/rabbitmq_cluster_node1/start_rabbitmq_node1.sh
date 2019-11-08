#!/bin/bash

~/title.sh "RabbitMQ Node 1"

export RABBITMQ_CONF_ENV_FILE=/Users/jeffrey/tmp3/rabbitmq-env.config
clear && /usr/local/Cellar/rabbitmq/3.6.14/sbin/rabbitmq-server
