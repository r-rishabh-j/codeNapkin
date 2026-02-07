import QRCode from 'qrcode';
import { getLocalIp } from '../utils/network';

/** Generate a QR code data URL for the connection */
export async function generateQrCode(port: number, token: string): Promise<{
  qrDataUrl: string;
  connectionUrl: string;
}> {
  const ip = getLocalIp();
  const connectionUrl = `http://${ip}:${port}/web-client/?token=${token}`;
  const qrDataUrl = await QRCode.toDataURL(connectionUrl, {
    width: 300,
    margin: 2,
    color: { dark: '#000000', light: '#ffffff' },
  });
  return { qrDataUrl, connectionUrl };
}
