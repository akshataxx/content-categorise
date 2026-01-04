# Deployment Guide - Content Categorization Backend

## How to Update the Instance with New Code

When you make code changes and want to deploy them to production, follow these steps:

### Step 1: Build and Push New Docker Image (Local Machine)

```bash
# Make sure you're in the project root directory
# Build for AMD64 architecture and push to GCR
docker buildx build --platform linux/amd64 -t gcr.io/content-categorisation/content-app:latest --push .
```

**Alternative (if buildx has issues):**
```bash
docker build -t gcr.io/content-categorisation/content-app:latest .
docker push gcr.io/content-categorisation/content-app:latest
```

### Step 2: SSH into GCE Instance

```bash
gcloud compute ssh content-backend-vm --zone=australia-southeast1-b
```

### Step 3: Pull Latest Image and Restart

```bash
# Pull the new image from GCR
docker-compose pull app

# Restart only the app container (keeps database running)
docker-compose up -d app
```

### Step 4: Verify Deployment

```bash
# Check container status
docker-compose ps

# Watch logs to ensure successful startup
docker-compose logs -f app
```

Press `Ctrl+C` to exit logs once you see "Started ContentApplication"

### Step 5: Test from Outside

```bash
# Exit SSH
exit

# Test from your local machine
curl http://34.151.189.90:8080/actuator/health
```

---

## Quick Commands Reference

### View Running Containers
```bash
docker-compose ps
```

### View App Logs
```bash
docker-compose logs -f app
```

### View Database Logs
```bash
docker-compose logs -f postgres
```

### Restart Everything (App + Database)
```bash
docker-compose restart
```

### Stop Everything
```bash
docker-compose down
```

### Stop and Remove Containers (keeps data volume)
```bash
docker-compose down
```

### Check Docker Images on Server
```bash
docker images | grep content-app
```

### Remove Old/Unused Images (cleanup)
```bash
docker image prune -a
```

---

## Database Migrations

Your Flyway migrations run automatically on app startup. When you:
1. Add new migration files in `src/main/resources/db/migration/`
2. Build and deploy the new image
3. The app will automatically apply new migrations on startup

---

## Environment Variables

To update environment variables:

### Step 1: SSH into Instance
```bash
gcloud compute ssh content-backend-vm --zone=australia-southeast1-b
```

### Step 2: Edit .env File
```bash
nano ~/.env
```

Make your changes, then `Ctrl+X`, `Y`, `Enter` to save.

### Step 3: Restart Containers
```bash
docker-compose up -d
```

---

## Troubleshooting

### App Won't Start
```bash
# Check logs for errors
docker-compose logs app

# Common issues:
# - Missing environment variables in .env
# - Database connection issues
# - Port conflicts
```

### Database Issues
```bash
# Check database logs
docker-compose logs postgres

# Connect to database directly
docker exec -it content-postgres psql -U postgres -d contentdb
```

### Check Resource Usage
```bash
docker stats
```

### Complete Reset (WARNING: Deletes all data)
```bash
docker-compose down -v  # Removes containers AND data volumes
docker-compose up -d    # Start fresh
```

---

## Important Files on GCE Instance

- `~/docker-compose.yml` - Container orchestration config
- `~/.env` - Environment variables and secrets
- Docker volume `pgdata` - PostgreSQL data (persistent)

---

## GCP Project Details

- **Project ID:** `content-categorisation`
- **Instance Name:** `content-backend-vm`
- **Zone:** `australia-southeast1-b`
- **External IP:** `34.151.189.90`
- **App Port:** `8080`
- **Container Registry:** `gcr.io/content-categorisation/content-app:latest`

---

## CI/CD Integration (Future)

When you're ready to automate deployments:

1. Use **Google Cloud Build** to build and push images automatically on git push
2. Use **Cloud Run** for serverless container deployment (alternative to GCE)
3. Use **Kubernetes (GKE)** for advanced orchestration and scaling

For now, the manual process above works great for development and small-scale production!
