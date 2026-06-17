import { useState } from "react";

export function greetingUrl(name: string): string {
  const params = name ? `?name=${encodeURIComponent(name)}` : "";
  return `/api/greeting${params}`;
}

export default function App() {
  const [name, setName] = useState("");
  const [message, setMessage] = useState("");

  async function fetchGreeting() {
    const response = await fetch(greetingUrl(name));
    const body = (await response.json()) as { message: string };
    setMessage(body.message);
  }

  return (
    <main>
      <h1>Agent Sandbox</h1>
      <input
        aria-label="name"
        value={name}
        onChange={(event) => setName(event.target.value)}
      />
      <button onClick={fetchGreeting}>Greet</button>
      {message && <p role="status">{message}</p>}
    </main>
  );
}
