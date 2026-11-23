#!/usr/bin/env python3
"""
Benchmark automático: compila o Java, roda os experimentos (sequencial, paralelo,
distribuído) e grava resultados.csv + info_sistema.txt.

Uso:
    python benchmark.py --teste      # roda uma versão minúscula, só para conferir se tudo funciona
    python benchmark.py              # benchmark completo (reps=6; a rep 0 é aquecimento)
"""
import argparse, glob, os, platform, subprocess, sys, time

HEADER = ("rep,versao,linhas,colunas,geracoes,pct,limiar,prob,parada,paralelismo,"
          "t_total,t_calc,t_sync,geracoes_exec,ignorantes,espalhadores,inativos,checksum")
TIMEOUT = 900
PORTA_BASE = 5000
LIMIAR = 3   # limiar de convencimento usado em todos os experimentos (opção --limiar)


def info_sistema():
    if sys.platform.startswith("win"):
        # PowerShell em vez de wmic: o wmic foi removido das versões recentes do Windows 11.
        ps = ["powershell", "-NoProfile", "-Command"]
        cmds = [ps + ["Get-CimInstance Win32_Processor | Format-List Name,NumberOfCores,NumberOfLogicalProcessors"],
                ps + ["Get-CimInstance Win32_ComputerSystem | Format-List TotalPhysicalMemory"]]
    elif sys.platform == "darwin":
        cmds = [["sysctl", "-n", "machdep.cpu.brand_string"],
                ["sysctl", "-n", "hw.physicalcpu", "hw.logicalcpu", "hw.memsize"]]
    else:
        cmds = [["lscpu"], ["free", "-h"]]
    cmds += [["java", "-version"], ["javac", "-version"]]
    with open("info_sistema.txt", "w", encoding="utf-8") as f:
        f.write(f"SO: {platform.platform()}\nPython: {sys.version.split()[0]}\n")
        f.write(f"Nucleos logicos (os.cpu_count): {os.cpu_count()}\n\n")
        for c in cmds:
            f.write("$ " + " ".join(c) + "\n")
            try:
                r = subprocess.run(c, capture_output=True, text=True, timeout=30)
                f.write(r.stdout + r.stderr + "\n")
            except Exception as e:
                f.write(f"(falhou: {e})\n\n")


def montar_runs(rapido, nuc):
    """Lista ordenada e sem repetição de (problema, versao, paralelismo)."""
    if rapido:
        base = dict(linhas=400, geracoes=20, pct=0.05, prob=1.0, parada=0)
        tams, gers, pcts, ps, g_long = [200, 400], [10, 20], [0.05, 0.20], [1, 2], 40
    else:
        base = dict(linhas=2000, geracoes=50, pct=0.05, prob=1.0, parada=0)
        tams, gers, pcts = [500, 1000, 2000, 4000], [10, 50, 100, 200], [0.01, 0.05, 0.10, 0.20]
        ps, g_long = sorted({1, 2, 4, 8, nuc}), 200
    P0 = min(4, nuc)
    runs = {}

    def add(prob, P):
        chave = tuple(sorted(prob.items()))
        runs[(chave, "sequencial", 1)] = None
        runs[(chave, "paralelo", P)] = None
        runs[(chave, "distribuido", P)] = None

    for t in tams: add({**base, "linhas": t}, P0)                       # tamanho da matriz
    for g in gers: add({**base, "geracoes": g}, P0)                     # nº de gerações
    for pc in pcts: add({**base, "pct": pc}, P0)                        # % inicial
    for P in ps: add(base, P)                                           # threads / workers
    for pc in (0.05, 0.20):                                             # parada antecipada
        add({**base, "geracoes": g_long, "pct": pc, "parada": 0}, P0)
        add({**base, "geracoes": g_long, "pct": pc, "parada": 1}, P0)
    for pr in (1.0, 0.7, 0.4):                                          # prob. de convencimento
        add({**base, "pct": 0.10, "prob": pr}, P0)
    return list(runs.keys())


def args_problema(pr):
    return [f"linhas={pr['linhas']}", f"colunas={pr['linhas']}", f"geracoes={pr['geracoes']}",
            f"pct={pr['pct']}", f"limiar={LIMIAR}", f"prob={pr['prob']}", f"parada={pr['parada']}"]


def extrair_csv(texto):
    for linha in texto.splitlines():
        if linha.startswith("CSV,"):
            return linha[4:].strip()
    raise RuntimeError("linha CSV não encontrada na saída:\n" + texto)


def rodar(versao, pr, P, porta):
    a = args_problema(pr)
    kw = dict(capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=TIMEOUT)
    if versao == "sequencial":
        return extrair_csv(subprocess.run(["java", "-cp", ".", "FakeNewsSequencial"] + a, **kw).stdout)
    if versao == "paralelo":
        return extrair_csv(subprocess.run(["java", "-cp", ".", "FakeNewsParallel", f"threads={P}"] + a, **kw).stdout)
    # distribuído: mestre e workers em processos separados
    mestre = subprocess.Popen(["java", "-cp", ".", "FakeNewsMaster", f"workers={P}", f"porta={porta}"] + a,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                              encoding="utf-8", errors="replace")
    mestre.stdout.readline()  # espera "PRONTO" (porta aberta)
    workers = [subprocess.Popen(["java", "-cp", ".", "FakeNewsWorker", "host=localhost", f"porta={porta}"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) for _ in range(P)]
    try:
        saida = mestre.communicate(timeout=TIMEOUT)[0]
        for w in workers: w.wait(timeout=60)
    except subprocess.TimeoutExpired:
        mestre.kill(); [w.kill() for w in workers]; raise
    return extrair_csv(saida)


def main():
    global LIMIAR
    ap = argparse.ArgumentParser()
    ap.add_argument("--teste", action="store_true", help="versão minúscula para testar")
    ap.add_argument("--reps", type=int, default=None)
    ap.add_argument("--limiar", type=int, default=3,
                    help="limiar de convencimento (padrão 3; com 2 a propagação dura mais gerações)")
    o = ap.parse_args()
    LIMIAR = o.limiar
    reps = o.reps if o.reps is not None else (2 if o.teste else 6)
    nuc = os.cpu_count() or 4

    info_sistema()
    print("Compilando...")
    subprocess.run(["javac"] + glob.glob("*.java"), check=True)

    runs = montar_runs(o.teste, nuc)
    total = len(runs) * reps
    print(f"{len(runs)} configurações x {reps} repetições = {total} execuções")

    linhas_csv, feito, porta = [], 0, PORTA_BASE
    inicio = time.time()
    with open("resultados.csv", "w", encoding="utf-8") as f:
        f.write(HEADER + "\n")
        for (chave, versao, P) in runs:
            pr = dict(chave)
            for rep in range(reps):
                porta = PORTA_BASE + (feito % 2000)
                linha = rodar(versao, pr, P, porta)
                f.write(f"{rep},{linha}\n"); f.flush()
                linhas_csv.append((rep, linha))
                feito += 1
                dec = (time.time() - inicio) / feito * (total - feito)
                print(f"[{feito}/{total}] {versao} P={P} {pr['linhas']}x{pr['linhas']} "
                      f"g={pr['geracoes']} pct={pr['pct']} prob={pr['prob']} parada={pr['parada']} "
                      f"rep={rep} (faltam ~{dec/60:.1f} min)")

    # Validação: mesmo problema => mesmo checksum e mesmas gerações, em qualquer versão
    grupos = {}
    for rep, l in linhas_csv:
        c = l.split(",")
        # c: versao,linhas,colunas,geracoes,pct,limiar,prob,parada,paralelismo,tt,tc,ts,gexec,ig,es,in,chk
        # A chave é só o PROBLEMA (c[1:8]); o paralelismo (c[8]) fica de fora, senão o
        # sequencial (P=1) nunca seria comparado com paralelo/distribuído com P>1.
        grupos.setdefault(tuple(c[1:8]), set()).add((c[12], c[13], c[14], c[15], c[16]))
    erros = [k for k, v in grupos.items() if len(v) != 1]
    if erros:
        print("\n*** ATENÇÃO: resultados DIFERENTES entre versões em:", erros)
    else:
        print("\nVALIDAÇÃO OK: sequencial, paralelo e distribuído produziram resultados idênticos.")


if __name__ == "__main__":
    main()
