export function notFound(req, res) {
  res.status(404).json({ error: 'Rota nao encontrada' });
}

export function errorHandler(error, req, res, next) {
  if (res.headersSent) return next(error);
  console.error(error);
  const status = error.name === 'ZodError' ? 400 : (error.status ?? 500);
  res.status(status).json({
    error: status === 500 ? 'Falha interna do servidor' : error.message,
    ...(error.name === 'ZodError' ? { details: error.issues } : {})
  });
}
