import { hash } from '@node-rs/argon2';
import { stdin, stdout } from 'node:process';
import { createInterface } from 'node:readline/promises';

const rl = createInterface({ input: stdin, output: stdout });
const password = await rl.question('Password: '); rl.close();
if (password.length < 12) throw new Error('Password must be at least 12 characters');
console.log(await hash(password, { algorithm: 2, memoryCost: 65536, timeCost: 3, parallelism: 1 }));
