# Ollama Android Client

A native, high-performance Android client for interacting with self-hosted **Ollama** instances. Experience private, local AI with a clean Material 3 interface, real-time streaming, and multimodal support.

---

## 🚀 Features

* **Real-time Streaming:** Responses appear word-by-word as the model generates them.
* **Multimodal (Vision):** Upload images to analyze them using vision-capable models (e.g., `llava`).
* **Voice Integration:**
    * **Speech-to-Text:** Input prompts using the microphone.
    * **Text-to-Speech:** Automatically read assistant responses aloud.
* **Session Management:** Create, rename, and delete multiple chat threads; history is saved locally.
* **Customizable Config:** Change system prompts, server IPs, and switch models on the fly.

---

## 🖥️ Server Setup (Windows)

Ollama, by default, only allows connections from the machine it is installed on (`localhost`). To allow your Android phone to connect over your Wi-Fi, you **must** follow these steps:

1.  **Quit Ollama:** Right-click the Ollama icon in the system tray (near the clock) and select **Quit**.
2.  **Open Environment Variables:**
    * Press the `Windows Key` and search for **"Edit the system environment variables"**.
    * Click the **Environment Variables** button at the bottom right.
3.  **Add New Variable:**
    * Under the **User variables** section, click **New**.
    * **Variable name:** `OLLAMA_HOST`
    * **Variable value:** `0.0.0.0`
4.  **Restart Ollama:** Open the Ollama application from your Start menu.
5.  **Firewall:** Ensure port `11434` is allowed for "Private" networks in your Windows Firewall settings.

## 🍎 Server Setup (macOS / Linux)

1. Run the following command in your terminal before launching the Ollama server:

```bash
export OLLAMA_HOST=0.0.0.0
ollama serve
```

 ## ⚖️ License


Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
