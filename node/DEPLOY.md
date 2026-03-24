# Deploying to Vercel

## Prerequisites

- A [Vercel account](https://vercel.com)
- Vercel CLI installed:
  ```bash
  npm install -g vercel
  ```
- Your Auth0 application credentials (Client ID, Client Secret, Domain)

---

## Step 1 — Connect the repo to Vercel

### Option A: Via the Vercel dashboard (easiest)
1. Go to [vercel.com/new](https://vercel.com/new)
2. Click **"Import Git Repository"** and select `saltine0/VolunteerApp-node`
3. Leave the framework preset as **Other**
4. Do **not** deploy yet — set environment variables first (Step 2)

### Option B: Via the CLI
```bash
cd /path/to/node
vercel
```
Follow the prompts. When asked to link to an existing project, choose **No** to create a new one.

---

## Step 2 — Set up MongoDB Atlas (free, persistent data)

The app stores all data in JSON files by default, which do not persist on Vercel's serverless
platform. Setting the `MONGODB_URI` environment variable tells the app to use MongoDB instead,
which persists across deployments and requests.

**MongoDB Atlas M0 is free forever** — no credit card required, no compute-hour limits,
512 MB storage. It is the recommended option for this app.

### 2a — Create an Atlas account and cluster

1. Go to [cloud.mongodb.com](https://cloud.mongodb.com) and sign up for a free account.
2. After logging in, click **"Build a Database"**.
3. Choose **M0 Free** tier.
4. Select any cloud provider and region (pick one close to your Vercel deployment region).
5. Give the cluster a name (e.g. `volunteer-app`) and click **"Create"**.

### 2b — Create a database user

1. In the left sidebar, go to **Security → Database Access**.
2. Click **"Add New Database User"**.
3. Choose **Password** authentication.
4. Enter a username (e.g. `appuser`) and a strong password. Save these — you'll need them shortly.
5. Under "Database User Privileges", select **"Read and write to any database"**.
6. Click **"Add User"**.

### 2c — Allow network access

1. In the left sidebar, go to **Security → Network Access**.
2. Click **"Add IP Address"**.
3. Click **"Allow Access from Anywhere"** (this sets `0.0.0.0/0`).
   > This is required for Vercel because its serverless functions use dynamic IP addresses.
4. Click **"Confirm"**.

### 2d — Get your connection string

1. In the left sidebar, go to **Deployment → Database**.
2. Click **"Connect"** on your cluster.
3. Choose **"Drivers"**.
4. Select **Node.js** and version **5.5 or later**.
5. Copy the connection string. It will look like:
   ```
   mongodb+srv://appuser:<password>@volunteer-app.xxxxx.mongodb.net/?retryWrites=true&w=majority
   ```
6. Replace `<password>` with the password you set in Step 2b.

---

## Step 3 — Set environment variables in Vercel

In the Vercel dashboard, go to your project → **Settings → Environment Variables** and add each of the following:

| Name | Value |
|---|---|
| `AUTH0_CLIENT_ID` | Your Auth0 Client ID |
| `AUTH0_CLIENT_SECRET` | Your Auth0 Client Secret |
| `AUTH0_DOMAIN` | e.g. `dev-abc123.us.auth0.com` |
| `BASE_URL` | Your Vercel URL, e.g. `https://volunteer-app.vercel.app` |
| `SESSION_SECRET` | Any long random string |
| `MONGODB_URI` | The connection string from Step 2d |

> **Do not set `PORT`** — Vercel manages the port automatically.

Or use the CLI:
```bash
vercel env add MONGODB_URI
vercel env add AUTH0_CLIENT_ID
vercel env add AUTH0_CLIENT_SECRET
vercel env add AUTH0_DOMAIN
vercel env add BASE_URL
vercel env add SESSION_SECRET
```

---

## Step 4 — Update Auth0 callback URLs

In your [Auth0 application settings](https://manage.auth0.com), update:

- **Allowed Callback URLs:** `https://your-app.vercel.app/callback`
- **Allowed Logout URLs:** `https://your-app.vercel.app`

Replace `your-app.vercel.app` with your actual Vercel URL.

---

## Step 5 — Deploy

### Via dashboard
Click **Deploy** in the Vercel project page.

### Via CLI
```bash
vercel --prod
```

Vercel will build and deploy the app. When it finishes it will print your live URL.

---

## Redeploying after changes

Any push to the `main` branch will automatically trigger a new deployment if you connected via
the dashboard. To deploy manually:

```bash
git add .
git commit -m "your message"
git push
```

---

## Migrating existing local data to Atlas (optional)

If you have tasks and sign-ups in your local `data/data.json` that you want to carry over:

1. Install `mongosh` (the MongoDB shell) from [mongodb.com/try/download/shell](https://www.mongodb.com/try/download/shell).

2. Open `data/data.json` and copy the contents of the `tasks` array.

3. Connect to your Atlas cluster:
   ```bash
   mongosh "mongodb+srv://appuser:<password>@volunteer-app.xxxxx.mongodb.net/volunteer_signup"
   ```

4. Insert your tasks:
   ```js
   db.app_data.replaceOne(
     { _id: "tasks" },
     { _id: "tasks", tasks: [ /* paste your tasks array here */ ] },
     { upsert: true }
   )
   ```

5. Verify it worked:
   ```js
   db.app_data.findOne({ _id: "tasks" })
   ```

---

## Running locally with MongoDB (optional)

You can also point your local `.env` at your Atlas cluster to test the MongoDB path locally:

```
MONGODB_URI=mongodb+srv://appuser:<password>@volunteer-app.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

If `MONGODB_URI` is not set, the app automatically falls back to the local JSON files — so
local development without MongoDB continues to work as before.

---

## Step 6 — Enable email reminders on Vercel

The in-process `node-cron` job requires a long-running server and will not fire on Vercel's
serverless platform. Instead, a dedicated endpoint (`/api/run-reminders`) is called on a
schedule by either Vercel Cron Jobs (Pro plan) or a free external scheduler.

### 6a — Add `CRON_SECRET` to Vercel environment variables

Generate a long random string (e.g. run `openssl rand -hex 32` in your terminal) and add it
as an environment variable in your Vercel project:

| Name | Value |
|---|---|
| `CRON_SECRET` | Your random secret string |

Via CLI:
```bash
vercel env add CRON_SECRET
```

### 6b — Option A: Vercel Cron Jobs (Pro plan only)

`vercel.json` already includes the cron definition:

```json
"crons": [
  { "path": "/api/run-reminders", "schedule": "0 * * * *" }
]
```

Vercel will call `GET /api/run-reminders` every hour. For Vercel-initiated calls the
`Authorization` header is set automatically using the `CRON_SECRET` value — no extra
configuration needed.

> **Requires a Vercel Pro plan.** Cron Jobs are not available on the free Hobby tier.

### 6b — Option B: cron-job.org (free, works on Hobby tier)

[cron-job.org](https://cron-job.org) is a free external scheduler that can call your
endpoint on any schedule.

1. Go to [cron-job.org](https://cron-job.org) and create a free account.
2. Click **"Create cronjob"**.
3. Set the **URL** to:
   ```
   https://your-app.vercel.app/api/run-reminders
   ```
   Replace `your-app.vercel.app` with your actual Vercel URL.
4. Set the **schedule** to hourly (every hour at minute 0), or match the time you configured
   in the app's email settings.
5. Under **"Advanced"**, add a request header:
   - **Name:** `Authorization`
   - **Value:** `Bearer <your CRON_SECRET value>`
6. Save the job.

cron-job.org will now call your endpoint on schedule, which triggers the reminder emails
exactly as the local cron job did.
