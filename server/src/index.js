import { app } from './app.js';
import { config } from './config.js';

app.listen(config.PORT, config.HOST, () => {
  console.log(`Yugidex em http://localhost:${config.PORT} (rede: ${config.HOST}:${config.PORT})`);
});
