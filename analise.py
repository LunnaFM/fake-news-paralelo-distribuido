#!/usr/bin/env python3
"""
Análise do resultados.csv gerado pelo benchmark.py (só biblioteca padrão).

1) Validação: para cada PROBLEMA (mesmos linhas, colunas, geracoes, pct, limiar, prob, parada),
   confere se sequencial, paralelo e distribuído produziram exatamente as mesmas gerações
   executadas, as mesmas contagens e o mesmo checksum, com qualquer número de threads/workers.
2) Desempenho: mediana do tempo total de cada configuração (descartando a rep 0, quando há
   mais de uma repetição), speedup (T_seq / T) e eficiência (speedup / P).
   Grava também tabela_resultados.csv.

Uso:
    python analise.py                  # lê resultados.csv
    python analise.py outro_arquivo.csv
"""
import csv
import statistics
import sys
from collections import defaultdict

PROBLEMA = ("linhas", "colunas", "geracoes", "pct", "limiar", "prob", "parada")
RESULTADO = ("geracoes_exec", "ignorantes", "espalhadores", "inativos", "checksum")


def ler(arquivo):
    # utf-8-sig: aceita o arquivo com ou sem BOM (o Excel e o Bloco de Notas gravam com BOM).
    with open(arquivo, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def validar(linhas):
    """Retorna True se todas as versões concordam em todos os problemas."""
    resultados = defaultdict(set)   # problema -> conjunto de resultados distintos observados
    versoes = defaultdict(set)      # problema -> versões que rodaram esse problema
    for r in linhas:
        prob = tuple(r[c] for c in PROBLEMA)
        resultados[prob].add(tuple(r[c] for c in RESULTADO))
        versoes[prob].add(r["versao"])

    divergentes = [p for p, v in resultados.items() if len(v) != 1]
    incompletos = [p for p, v in versoes.items() if len(v) < 3]

    print(f"Problemas distintos: {len(resultados)}  |  execuções: {len(linhas)}")
    if incompletos:
        print(f"AVISO: {len(incompletos)} problema(s) sem as três versões:")
        for p in incompletos:
            print("   ", dict(zip(PROBLEMA, p)), "->", sorted(versoes[p]))
    if divergentes:
        print(f"*** FALHA: {len(divergentes)} problema(s) com resultados DIFERENTES entre versões:")
        for p in divergentes:
            print("   ", dict(zip(PROBLEMA, p)))
        return False
    print("VALIDAÇÃO OK: mesmas gerações, contagens e checksum em todas as versões, em todos os problemas.")
    return True


def desempenho(linhas):
    """Imprime e grava a tabela de medianas, speedup e eficiência."""
    varias_reps = any(int(r["rep"]) > 0 for r in linhas)
    tempos = defaultdict(list)      # (problema, versao, P) -> tempos totais em segundos
    for r in linhas:
        if varias_reps and int(r["rep"]) == 0:
            continue                # rep 0 descartada
        chave = (tuple(r[c] for c in PROBLEMA), r["versao"], int(r["paralelismo"]))
        tempos[chave].append(float(r["t_total"]))
    mediana = {k: statistics.median(v) for k, v in tempos.items()}

    def ordem(chave):
        prob, versao, p = chave
        return (tuple(float(x) for x in prob), p, versao)

    cab = ["linhas", "geracoes", "pct", "limiar", "prob", "parada", "versao", "P",
           "t_mediana_ms", "speedup", "eficiencia_pct"]
    tabela = []
    for chave in sorted(mediana, key=ordem):
        prob, versao, p = chave
        d = dict(zip(PROBLEMA, prob))
        t = mediana[chave]
        t_seq = mediana.get((prob, "sequencial", 1))
        if versao == "sequencial" or t_seq is None or t <= 0:
            speedup, efic = "", ""
        else:
            s = t_seq / t
            speedup, efic = f"{s:.2f}", f"{s / p * 100:.1f}"
        tabela.append([d["linhas"], d["geracoes"], d["pct"], d["limiar"], d["prob"], d["parada"],
                       versao, p, f"{t * 1000:.1f}", speedup, efic])

    larguras = [max(len(str(x)) for x in col) for col in zip(cab, *tabela)] if tabela else [len(c) for c in cab]
    print()
    print("  ".join(str(c).rjust(w) for c, w in zip(cab, larguras)))
    for linha in tabela:
        print("  ".join(str(c).rjust(w) for c, w in zip(linha, larguras)))

    with open("tabela_resultados.csv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(cab)
        w.writerows(tabela)
    print("\nTabela gravada em tabela_resultados.csv")


def main():
    arquivo = sys.argv[1] if len(sys.argv) > 1 else "resultados.csv"
    linhas = ler(arquivo)
    if not linhas:
        sys.exit(f"{arquivo} está vazio.")
    ok = validar(linhas)
    desempenho(linhas)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
