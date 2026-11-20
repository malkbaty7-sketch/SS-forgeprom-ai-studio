import process from 'node:process';

/**
 * إغلاق لطيف: يتوقف الخادم عن قبول اتصالات جديدة، يمنح الطلبات الجارية
 * مهلة محدودة، ثم يخرج. أي تجاوز للمهلة يخرج بالرمز 1 كي يعيد المدير
 * تشغيل العملية بدل تعليقها.
 */
export function shutdown(server, {
  signals = ['SIGTERM', 'SIGINT'],
  timeoutMs = 10_000,
  logger = console,
  exit = code => process.exit(code)
} = {}) {
  let closing = false;
  const handler = signal => {
    if (closing) return;
    closing = true;
    try { logger?.info?.(JSON.stringify({ level: 'info', event: 'shutdown', signal, timeoutMs })); } catch { /* noop */ }
    const timer = setTimeout(() => {
      try { logger?.error?.(JSON.stringify({ level: 'error', event: 'shutdown_timeout', signal, timeoutMs })); } catch { /* noop */ }
      exit(1);
    }, timeoutMs);
    if (typeof timer.unref === 'function') timer.unref();
    server.close(error => {
      clearTimeout(timer);
      exit(error ? 1 : 0);
    });
    // لا ننتظر keep-alive الخامل؛ الطلبات الجارية وحدها تُمنح المهلة.
    server.closeIdleConnections?.();
  };
  for (const signal of signals) process.on(signal, handler);
  return handler;
}
