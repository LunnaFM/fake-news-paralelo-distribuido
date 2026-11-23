/** Demonstração: sobe o mestre e 2 workers na mesma JVM (só para apresentar; NÃO usar para benchmark).
 *  Uso: java Main linhas=500 geracoes=30 */
public class Main {
    public static void main(String[] args) throws Exception {
        // "workers=2" vai PRIMEIRO: Simulacao.arg devolve a primeira ocorrência, então a demo
        // sempre usa 2 workers (os que são criados abaixo), mesmo que o usuário passe workers=N.
        String[] a = new String[args.length + 1];
        a[0] = "workers=2";
        System.arraycopy(args, 0, a, 1, args.length);

        // Os workers usam a mesma porta do mestre (porta=... do usuário ou 5000).
        final String porta = Simulacao.arg(args, "porta", "5000");

        new Thread(() -> {
            try { FakeNewsMaster.main(a); } catch (Exception e) { e.printStackTrace(); }
        }).start();
        Thread.sleep(500);
        for (int i = 0; i < 2; i++)
            new Thread(() -> FakeNewsWorker.main(new String[]{"host=localhost", "porta=" + porta})).start();
    }
}
