import { sha256 as sha256Lib } from 'js-sha256'

export function sha256(input: string): string {
  return sha256Lib(input)
}

export async function generateFileKey(): Promise<string> {
  const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
  const exported = await crypto.subtle.exportKey('raw', key)
  return btoa(String.fromCharCode(...new Uint8Array(exported)))
}

function bytesFromHex(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2)
  for (let i = 0; i < hex.length; i += 2) bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16)
  return bytes
}

async function encryptWithRawKey(keyBytes: Uint8Array, plaintext: ArrayBuffer): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey('raw', keyBytes, 'AES-GCM', false, ['encrypt'])
  const nonce = crypto.getRandomValues(new Uint8Array(12))
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: nonce }, key, plaintext)
  const result = new Uint8Array(nonce.length + ciphertext.byteLength)
  result.set(nonce)
  result.set(new Uint8Array(ciphertext), nonce.length)
  return result.buffer
}

export async function encryptFileKey(passwordHash: string, fileKey: string): Promise<string> {
  const keyBytes = bytesFromHex(passwordHash)
  const encoded = new TextEncoder().encode(fileKey)
  const encrypted = await encryptWithRawKey(keyBytes, encoded.buffer)
  return btoa(String.fromCharCode(...new Uint8Array(encrypted)))
}
