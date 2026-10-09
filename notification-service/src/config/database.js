const { Pool } = require('pg');
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 5,
  connectionTimeoutMillis: 2000,
  statement_timeout: 5000,
});
module.exports = pool;
