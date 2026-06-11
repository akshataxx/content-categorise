# Deployment Guide - Content Categorization Backend

## Architecture Overview

```
Internet (HTTPS:443) → Caddy → App (8081) → Postgres
                         ↓
                   Auto SSL via
                   Let's Encrypt
```

- **URL:** `https://scoopbackend.duckdns.org`
- Uses **duckdns.org** for free DNS (maps ` scoopbackend.duckdns.org` → `149.28.175.245``)
- **Caddy** handles HTTPS termination with auto-renewing Let's Encrypt certificates
- **Container image:** `ghcr.io/akshataxx/content-app:latest` (GitHub Container Registry)

---

## Files on the VM

All deployment files live in `~/content-backend/` on the VM:

| File | Purpose |
|------|---------|
| `docker-compose.prod.yml` | Container orchestration |
| `Caddyfile` | Caddy reverse proxy config |
| `config/` | App config directory |
| `~/.env` | Environment variables (secrets, one level up) |

---

## SSH into the VM

```bash
ssh root@149.28.175.245
ssh root@45.76.127.112

```

---

## How to Update the Backend with New Code

### Step 1: Build and Push New Docker Image (Local Machine)

```bash
docker buildx build --platform linux/amd64 -t ghcr.io/akshataxx/content-app:latest --push .
```

> Requires `docker login ghcr.io` with a GitHub personal access token (write:packages scope) if not already authenticated.

### Step 2: SSH into the VM

```bash
ssh root@45.76.127.112
```

### Step 3: Pull Latest Image and Restart App

```bash
cd ~/content-backend
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d app
```

### Step 4: Verify Deployment

```bash
docker compose -f docker-compose.prod.yml logs -f app
```

Press `Ctrl+C` once you see "Started ContentApplication"

### Step 5: Test from Outside

```bash
curl https://45-76-127-112.duckdns.org/actuator/health
```

---

## Initial Setup (First Time on a New VM)

### 1. DigitalOcean Firewall

Ensure ports 80, 443, and 22 are open on the droplet (DigitalOcean Console → Networking → Firewalls).

### 2. Copy Files to VM

```bash
scp docker-compose.prod.yml Caddyfile .env root@149.28.175.245:~/content-backend/
```

### 3. Authenticate with GitHub Container Registry

```bash
# On the VM
echo YOUR_GITHUB_PAT | docker login ghcr.io -u akshataxx --password-stdin
```

### 4. Start Services

```bash
cd ~/content-backend
docker-compose -f docker-compose.prod.yml up -d
```

### 5. Verify Caddy got the SSL certificate

```bash
docker-compose -f docker-compose.prod.yml logs caddy
```

---

## Quick Commands Reference

```bash
# View all containers
docker ps

# View app logs
docker compose -f docker-compose.prod.yml logs -f app

# View Caddy logs (SSL issues)
docker compose -f docker-compose.prod.yml logs -f caddy

# View database logs
docker compose -f docker-compose.prod.yml logs -f postgres

# Restart everything
docker-compose -f docker-compose.prod.yml restart

# Restart a single service
docker-compose -f docker-compose.prod.yml restart app

# Stop everything
docker-compose -f docker-compose.prod.yml down

# Cleanup unused images
docker image prune -a
```

---

## Environment Variables

To update environment variables:

```bash
# SSH into VM
ssh root@45.76.127.112

# Edit .env file
nano ~/.env

# Restart containers to pick up changes
cd ~/content-backend
docker-compose -f docker-compose.prod.yml up -d
```

Key variables:

| Variable | Description                                                                  |
|----------|------------------------------------------------------------------------------|
| `DOMAIN` | sslip.io domain — must match VM IP with dashes: `149-28-175-245.duckdns.org` |
| `APP_IMAGE` | Docker image — `ghcr.io/akshataxx/content-app:latest`                        |
| `POSTGRES_PASSWORD` | Database password                                                            |
| `JWT_SECRET` | JWT signing secret                                                           |
| `OPENAI_API_KEY` | OpenAI API key                                                               |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID                                                       |

---

## Database Migrations

Flyway migrations run automatically on app startup:
1. Add new migration files in `src/main/resources/db/migration/`
2. Build and deploy the new image
3. Migrations apply automatically on startup

---

## Troubleshooting

### SSL Certificate Issues
```bash
docker-compose -f docker-compose.prod.yml logs caddy
# Check that ports 80/443 are open in DigitalOcean firewall
# Check that DOMAIN in .env matches the VM's IP with dashes
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

## VM Details

| Setting | Value                                  |
|---------|----------------------------------------|
| Provider | Vultr                                  |
| Hostname | backend-vm-small                       |
| External IP | `45.76.127.112`                       |
| HTTPS URL | `149-28-175-245.duckdns.org`      |
| Container Registry | `ghcr.io/akshataxx/content-app:latest` |
| Deployment Dir | `~/content-backend/`                   |
