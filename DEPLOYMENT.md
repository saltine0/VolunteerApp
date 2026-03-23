# Deploying on Oracle Cloud Free Tier

This guide walks through deploying the Volunteer Sign-Up app on Oracle Cloud Infrastructure (OCI) using Always Free resources.

---

## What You'll Need

- An Oracle Cloud account ([sign up at cloud.oracle.com](https://cloud.oracle.com) — no credit card required for Always Free)
- Your Auth0 credentials (`AUTH0_CLIENT_ID`, `AUTH0_CLIENT_SECRET`, `AUTH0_DOMAIN`)
- The GitHub repository URL: `https://github.com/saltine0/VolunteerApp.git`

---

## Step 1 — Create a Compute Instance

1. Log in to the [OCI Console](https://cloud.oracle.com)
2. Navigate to **Compute → Instances → Create Instance**
3. Configure the instance:
   - **Name:** `volunteer-app`
   - **Image:** Ubuntu 22.04 (click *Change image*)
   - **Shape:** Click *Change shape* → select **Ampere** → `VM.Standard.A1.Flex`
     - Set **OCPUs: 1** and **Memory: 6 GB** (well within the Always Free 4 OCPU / 24 GB allowance)
4. Under **Networking**, ensure a public subnet is selected and **Assign a public IPv4 address** is checked
5. Under **Add SSH keys**, either upload your existing public key or have OCI generate one (download the private key — you'll need it to connect)
6. Click **Create**

Wait 2–3 minutes for the instance to reach the **Running** state. Note the **Public IP address** shown on the instance detail page.

---

## Step 2 — Open Port 80 in the Firewall

OCI blocks all ports by default. You need to open port 80 in two places.

### 2a — OCI Security List (cloud-level firewall)

1. On your instance detail page, click the **Subnet** link
2. Click **Security Lists → Default Security List**
3. Click **Add Ingress Rules** and add:
   - **Source CIDR:** `0.0.0.0/0`
   - **IP Protocol:** TCP
   - **Destination Port Range:** `80`
4. Click **Add Ingress Rules** again for port `22` (SSH) if not already present

### 2b — OS-level firewall (inside the VM)

You'll run these after connecting in Step 3:
```bash
sudo ufw allow 22
sudo ufw allow 80
sudo ufw enable
```

---

## Step 3 — Connect to the Instance

```bash
ssh -i /path/to/your/private-key.pem ubuntu@<YOUR_PUBLIC_IP>
```

> If you see a permissions error, run: `chmod 400 /path/to/your/private-key.pem`

---

## Step 4 — Install Java, Maven, and Nginx

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk maven nginx git
```

Verify Java is installed:
```bash
java -version
# should show: openjdk version "17..."
```

---

## Step 5 — Clone and Build the App

```bash
cd ~
git clone https://github.com/saltine0/VolunteerApp.git
cd VolunteerApp
mvn package -DskipTests
```

This produces a runnable JAR at `target/signup-1.0.0.jar`.

---

## Step 6 — Configure Environment Variables

Create a file to store your credentials securely:

```bash
sudo mkdir -p /etc/volunteer-app
sudo nano /etc/volunteer-app/env
```

Paste the following, replacing the placeholder values with your real credentials:

```
AUTH0_CLIENT_ID=your_client_id_here
AUTH0_CLIENT_SECRET=your_client_secret_here
AUTH0_DOMAIN=your_tenant.us.auth0.com
SERVER_PORT=8080
```

Restrict access to this file:
```bash
sudo chmod 600 /etc/volunteer-app/env
```

---

## Step 7 — Create a systemd Service

This makes the app start automatically on boot and restart if it crashes.

```bash
sudo nano /etc/systemd/system/volunteer-app.service
```

Paste the following:

```ini
[Unit]
Description=Volunteer Sign-Up App
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/VolunteerApp
EnvironmentFile=/etc/volunteer-app/env
ExecStart=/usr/bin/java -jar /home/ubuntu/VolunteerApp/target/signup-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable volunteer-app
sudo systemctl start volunteer-app
```

Check that it's running:

```bash
sudo systemctl status volunteer-app
```

You should see `Active: active (running)`. To view logs:

```bash
sudo journalctl -u volunteer-app -f
```

---

## Step 8 — Configure Nginx as a Reverse Proxy

The app runs on port 8080 internally. Nginx listens on port 80 and forwards traffic to it.

```bash
sudo nano /etc/nginx/sites-available/volunteer-app
```

Paste:

```nginx
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable the site and reload nginx:

```bash
sudo ln -s /etc/nginx/sites-available/volunteer-app /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

---

## Step 9 — Update Auth0 Callback URLs

The app uses Auth0 for login. You need to register your server's IP address with Auth0 so the OAuth redirect works.

1. Log in to [manage.auth0.com](https://manage.auth0.com)
2. Navigate to **Applications → Your App**
3. Add your server's public IP to the following fields:
   - **Allowed Callback URLs:** `http://<YOUR_PUBLIC_IP>/login/oauth2/code/auth0`
   - **Allowed Logout URLs:** `http://<YOUR_PUBLIC_IP>/`
   - **Allowed Web Origins:** `http://<YOUR_PUBLIC_IP>`
4. Click **Save Changes**

---

## Step 10 — Verify the Deployment

Open a browser and navigate to:
```
http://<YOUR_PUBLIC_IP>
```

You should see the Volunteer Sign-Up app. Clicking **Sign In** should redirect to Auth0 and back.

---

## Updating the App

When you push new code to GitHub, SSH into the server and run:

```bash
cd ~/VolunteerApp
git pull
mvn package -DskipTests
sudo systemctl restart volunteer-app
```

---

## Common Commands

| Task | Command |
|------|---------|
| Start the app | `sudo systemctl start volunteer-app` |
| Stop the app | `sudo systemctl stop volunteer-app` |
| Restart the app | `sudo systemctl restart volunteer-app` |
| View live logs | `sudo journalctl -u volunteer-app -f` |
| Check nginx status | `sudo systemctl status nginx` |
| Test nginx config | `sudo nginx -t` |

---

## Notes

- The app stores all data in `src/main/resources/data/data.json` on the server. Back this file up before updating the app.
- The `email-config.json` (SMTP settings) is also stored on the server and is not in the git repo. It will be auto-created the first time you save email settings.
- For HTTPS/SSL, use [Certbot with Let's Encrypt](https://certbot.eff.org/instructions?ws=nginx&os=ubuntufocal) — requires a domain name pointed at your IP.
