# Simple Makefile to build and run the app in dev or prod modes

APP_NAME ?= content-categorization
TAG ?= latest
DOCKER_IMAGE ?= $(APP_NAME):$(TAG)
PORT ?= 8080
ENV_FILE ?= .env

.PHONY: help clean build test run-dev run-prod docker-build docker-run-prod docker-push

help:
	@echo "Available targets:"
	@echo "  make build              - Build the project (fat JAR)"
	@echo "  make test               - Run tests"
	@echo "  make clean              - Clean build artifacts"
	@echo "  make run-dev            - Run locally with dev profile"
	@echo "  make run-prod           - Run locally with prod profile"
	@echo "  make docker-build       - Build Docker image ($(DOCKER_IMAGE))"
	@echo "  make docker-run-prod    - Run Docker image with prod profile using $$(ENV_FILE)"
	@echo "  make docker-push        - Push Docker image (requires DOCKER_REGISTRY)"

clean:
	./mvnw clean

build:
	./mvnw -T 1C clean package -DskipTests

test:
	./mvnw -T 1C test

# Local runs using Maven wrapper with specific Spring profile
run-dev:
	SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

run-prod:
	SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run

# Run by sourcing .env into the shell so Spring sees real environment variables
run-dev-dotenv:
	set -a; [ -f .env ] && . ./.env; set +a; ./mvnw spring-boot:run

run-prod-dotenv:
	set -a; [ -f .env ] && . ./.env; set +a; SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run

# Container image build and run
# Use `make docker-build TAG=prod` to tag differently

docker-build:
	docker build -t $(DOCKER_IMAGE) .

# Use an .env file to pass DB credentials and other settings
# Default reads from .env (set ENV_FILE to override)
# Ensure the image has been built before running

docker-run-prod:
	docker run --rm -p $(PORT):8080 --env-file $(ENV_FILE) -e SPRING_PROFILES_ACTIVE=prod $(DOCKER_IMAGE)

# Optionally push to a registry (set DOCKER_REGISTRY, e.g., ghcr.io/your-org)
# Usage: make docker-push TAG=prod DOCKER_REGISTRY=ghcr.io/your-org APP_NAME=content-categorization

docker-push:
	@if [ -z "$(DOCKER_REGISTRY)" ]; then echo "DOCKER_REGISTRY not set"; exit 1; fi
	docker tag $(DOCKER_IMAGE) $(DOCKER_REGISTRY)/$(APP_NAME):$(TAG)
	docker push $(DOCKER_REGISTRY)/$(APP_NAME):$(TAG)
