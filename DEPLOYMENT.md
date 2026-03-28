# Deployment Guide - Content Categorization Backend

## Architecture Overview

```
Internet (HTTPS:443) → Caddy → App (8081) → Postgres
                         ↓
                   Auto SSL via
                   Let's Encrypt
```

- **URL:** `https://34-151-189-90.sslip.io`
- Uses **sslip.io** for free DNS (maps `34-151-189-90.sslip.io` → `34.151.189.90`)
- **Caddy** handles HTTPS termination with auto-renewing Let's Encrypt certificates

---

## Files to Deploy

Copy these files to your VM's home directory (`~`):

| File | Purpose |
|------|---------|
| `docker-compose.prod.yml` | Container orchestration |
| `Caddyfile` | Caddy reverse proxy config |
| `.env` | Environment variables (secrets) |

---

## Initial Setup

### 1. GCP Firewall Rules

Ensure ports 80 and 443 are open:
- GCP Console → VPC Network → Firewall → Create rule
- Allow TCP ports `80` and `443` from `0.0.0.0/0`

### 2. Copy Files to VM

```bash
# From your local machine
gcloud compute scp docker-compose.prod.yml Caddyfile .env content-backend-vm:~ --zone=australia-southeast1-b
```

### 3. Start Services

```bash
# SSH into VM
gcloud compute ssh content-backend-vm --zone=australia-southeast1-b

# Start all services
docker-compose -f docker-compose.prod.yml up -d

# Verify Caddy got the SSL certificate
docker logs caddy
```

### 4. Test HTTPS

```bash
curl https://34-151-189-90.sslip.io/actuator/health
```

---

## How to Update the Instance with New Code

### Step 1: Build and Push New Docker Image (Local Machine)

```bash
docker buildx build --platform linux/amd64 -t gcr.io/content-categorisation/content-app:prod --push .
```

### Step 2: SSH into GCE Instance

```bash
gcloud compute ssh content-backend-vm --zone=australia-southeast1-b
```

### Step 3: Pull Latest Image and Restart

```bash
docker-compose -f docker-compose.prod.yml pull app
docker-compose -f docker-compose.prod.yml up -d app
```

### Step 4: Verify Deployment

```bash
docker-compose -f docker-compose.prod.yml logs -f app
```

Press `Ctrl+C` once you see "Started ContentApplication"

### Step 5: Test from Outside

```bash
curl https://34-151-189-90.sslip.io/actuator/health
```

---

## Quick Commands Reference

```bash
# View all containers
docker-compose -f docker-compose.prod.yml ps

# View app logs
docker-compose -f docker-compose.prod.yml logs -f app

# View Caddy logs (SSL issues)
docker-compose -f docker-compose.prod.yml logs -f caddy

# View database logs
docker-compose -f docker-compose.prod.yml logs -f postgres

# Restart everything
docker-compose -f docker-compose.prod.yml restart

# Stop everything
docker-compose -f docker-compose.prod.yml down

# Cleanup unused images
docker image prune -a
```

---

## Using a Custom Domain (Optional)

When you get a domain:

1. Point your domain's DNS A record to `34.151.189.90`
2. Update `Caddyfile`:
   ```
   api.yourdomain.com {
       reverse_proxy app:8081
   }
   ```
3. Restart Caddy:
   ```bash
   docker-compose -f docker-compose.prod.yml restart caddy
   ```

Caddy will automatically get a new certificate for your domain.

---

## Database Migrations

Flyway migrations run automatically on app startup:
1. Add new migration files in `src/main/resources/db/migration/`
2. Build and deploy the new image
3. Migrations apply automatically on startup

---

## Environment Variables

To update environment variables:

```bash
# SSH into VM
gcloud compute ssh content-backend-vm --zone=australia-southeast1-b

# Edit .env file
nano ~/.env

# Restart containers to pick up changes
docker-compose -f docker-compose.prod.yml up -d
```

---

## Troubleshooting

### SSL Certificate Issues
```bash
docker-compose -f docker-compose.prod.yml logs caddy
# Check that ports 80/443 are open in GCP firewall
```

### App Won't Start
```bash
docker-compose -f docker-compose.prod.yml logs app
# Check .env file has all required variables
```

### Database Issues
```bash
docker-compose -f docker-compose.prod.yml logs postgres

# Connect to database directly
docker exec -it content-postgres psql -U postgres -d contentdb
```

### Check Resource Usage
```bash
docker stats
```

### Complete Reset (WARNING: Deletes all data)
```bash
docker-compose -f docker-compose.prod.yml down -v
docker-compose -f docker-compose.prod.yml up -d
```

---

## Important Files on GCE Instance

| File | Purpose |
|------|---------|
| `~/docker-compose.prod.yml` | Container orchestration |
| `~/Caddyfile` | Caddy reverse proxy config |
| `~/.env` | Environment variables and secrets |
| Docker volume `pgdata` | PostgreSQL data (persistent) |
| Docker volume `caddy_data` | SSL certificates |

---

## GCP Project Details

| Setting | Value |
|---------|-------|
| Project ID | `content-categorisation` |
| Instance Name | `content-backend-vm` |
| Zone | `australia-southeast1-b` |
| External IP | `34.151.189.90` |
| HTTPS URL | `https://34-151-189-90.sslip.io` |
| Container Registry | `gcr.io/content-categorisation/content-app:prod` |
