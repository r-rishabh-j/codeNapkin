import * as os from 'os';

/**
 * Get the best local IP address for the phone to connect to.
 * Prefers WiFi interfaces (en0 on macOS, wlan0 on Linux).
 */
export function getLocalIp(): string {
  const interfaces = os.networkInterfaces();
  const preferred = ['en0', 'wlan0', 'Wi-Fi', 'eth0'];

  // Try preferred interfaces first
  for (const name of preferred) {
    const iface = interfaces[name];
    if (iface) {
      const ipv4 = iface.find(a => a.family === 'IPv4' && !a.internal);
      if (ipv4) return ipv4.address;
    }
  }

  // Fallback: find any non-internal IPv4 address
  for (const [, iface] of Object.entries(interfaces)) {
    if (!iface) continue;
    const ipv4 = iface.find(a => a.family === 'IPv4' && !a.internal);
    if (ipv4) return ipv4.address;
  }

  return '127.0.0.1';
}

/**
 * Get all available local IP addresses (for display in QR panel).
 */
export function getAllLocalIps(): string[] {
  const ips: string[] = [];
  const interfaces = os.networkInterfaces();

  for (const [, iface] of Object.entries(interfaces)) {
    if (!iface) continue;
    for (const addr of iface) {
      if (addr.family === 'IPv4' && !addr.internal) {
        ips.push(addr.address);
      }
    }
  }

  return ips;
}
