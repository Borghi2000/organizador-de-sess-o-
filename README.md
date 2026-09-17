# Estoque e validade — seção de chocolates

App Android pessoal para controlar estoque e validade da sua seção de produtos numa loja de
chocolates. Funciona sem internet, sem conta e sem cadastro: **os dados ficam só no seu aparelho**.

A câmera lê código de barras e etiqueta **dentro do próprio aparelho**, sem rede e sem custo por
foto. A única parte que usa internet é a ajuda por IA, que vem **desligada** e só funciona se você
ligar e colar a sua chave (veja [Câmera e leitura automática](#câmera-e-leitura-automática)).

---

## Como instalar no celular

1. Abra a aba **Releases** deste repositório e entre no release **APK mais recente**.
2. Baixe o arquivo que termina em `.apk`.
3. Toque no arquivo baixado. O Android vai pedir permissão para instalar apps de fora da Play
   Store — é o aviso normal, aceite para este arquivo.
4. Pronto. O app aparece como **Estoque Chocolate**.

Para atualizar depois, baixe o APK novo e instale por cima. **Os dados são preservados**, porque
todas as versões são assinadas com a mesma chave.

> **O arquivo tem cerca de 75 MB.** A maior parte disso são os modelos de reconhecimento de texto e
> de código de barras, que ficam **dentro do app** justamente para a câmera funcionar no corredor da
> loja sem depender de rede. Baixe no wi-fi; depois de instalado, o app não usa internet.

### Sobre a chave de assinatura

A chave fica em `keystore/` dentro deste repositório, visível para quem tiver acesso a ele. Foi uma
escolha consciente: é o que permite atualizar o app sem perder os dados e sem você ter que
configurar nada. Ela **não protege segredo nenhum** — serve só para o Android reconhecer que o APK
novo é continuação do anterior. Não use esta chave para publicar nada na Play Store.

---

## O dia a dia

A tela inicial mostra **só o que exige ação hoje**. Cartão zerado não aparece. Se estiver tudo em
ordem, o app diz isso com todas as letras em vez de encher a tela de números.

Os cartões, na ordem em que aparecem:

| Cartão | O que significa |
|---|---|
| Produtos vencidos | Validade já passou. Sempre no topo. |
| Vence em até 7 dias | Prioridade de venda. |
| Vence entre 8 e 15 dias | Planejar a saída. |
| Abaixo do estoque mínimo | Repor do depósito ou pedir. |
| Sem conferência recente | Lotes que não são contados há muito tempo. |
| Divergências abertas | Contagem que não bateu e ainda não foi resolvida. |
| Perdas do mês | O que foi descartado no mês corrente. |
| Sem estoque para venda | Existe no sistema, mas nada pode ser vendido. |

Os cartões de 7 e de 15 dias **não se sobrepõem**: um lote aparece em um cartão só, então os
números podem ser somados sem contar nada duas vezes.

### Sempre à mão

Os três comandos do dia a dia — **Contar**, **Repor** e **Perda** — ficam fixos na barra de baixo,
na zona do polegar, sem precisar rolar a tela. No topo há **Buscar**: uma caixa só, que acha produto
ou lote por nome, código, código de barras ou código do lote.

### Os nove comandos

| Comando | O que faz |
|---|---|
| **Registrar entrada** | Mercadoria chegando: produto, lote, validade, quantidade e local. |
| **Registrar contagem** | Você conta a prateleira e digita o número. Fica na tela para contar o próximo. |
| **Registrar perda** | Perda por vencimento, por avaria ou degustação — com motivo obrigatório nas duas primeiras. |
| **Registrar reposição** | Leva do depósito para a exposição, escolhendo sozinho o lote que vence primeiro. |
| **Mostrar vencimentos** | Tudo agrupado por faixa de validade, do que vence primeiro ao que vence depois. |
| **Mostrar faltas** | Quem está abaixo do mínimo, quanto pedir, e se há estoque no depósito para repor antes. |
| **Mostrar divergências** | Abertas primeiro, com a ação de ajustar o estoque pela contagem. |
| **Fechar conferência do dia** | Resume o que foi contado, mostra o que ficou de fora e carimba a data nos lotes. |
| **Gerar relatório semanal** | Semanal e mensal, para compartilhar como texto ou salvar em CSV. |

Saída, transferência e ajuste de inventário ficam na tela do lote: são movimentações, não rotinas
diárias.

### Confirmar antes ou desfazer depois

As duas coisas convivem, e a escolha entre elas é deliberada:

- **Ação rara e grave** — entrada, perda, ajuste de inventário, importar backup, limpar tudo —
  continua pedindo **confirmação antes**, com a tela que mostra o que vai ser gravado.
- **Ação repetitiva** — contagem e reposição — grava direto e deixa **"Desfazer" por 30 segundos**
  no aviso de baixo. Diálogo que aparece trinta vezes seguidas vira obstáculo que se aprende a
  apertar no automático, e aí ele não protege mais nada. Desfazer protege mais e atrapalha menos.

Desfazer repõe os lotes como estavam e apaga as movimentações e divergências que aquela ação criou.

### Aviso da manhã

Em **Ajustes → Aviso da manhã** você liga uma notificação diária no horário que escolher (padrão
8h): *"3 lotes vencem hoje, 2 abaixo do mínimo"*. **Se não houver pendência, o app não notifica** —
aviso que toca todo dia sem motivo vira ruído e acaba silenciado.

---

## As regras que o app aplica sozinho

**Faixas de validade.** Vencido é validade *anterior* a hoje; quem vence hoje é crítico, com zero
dia de prazo.

| Faixa | Prazo |
|---|---|
| Vencido | validade anterior a hoje |
| Crítico | vence em até 7 dias |
| Urgente | vence entre 8 e 15 dias |
| Atenção | vence entre 16 e 30 dias |
| Monitorar | vence entre 31 e 60 dias |
| Normal | vence em mais de 60 dias |

**PVPS — primeiro que vence, primeiro que sai.** Toda lista sai ordenada por validade. Quando você
pede uma quantidade maior do que o lote mais antigo tem, o app desce para o próximo e **grava uma
movimentação separada por lote**. Nunca existe um registro com quantidade somada de lotes
diferentes.

**Quatro estoques diferentes, nunca um só.** O app calcula separadamente: **físico** (tudo que está
na seção, inclusive vencido e segregado), **exposto** (gôndola e vitrine), **depósito** e
**disponível para venda**. Só o último conta como estoque de verdade. É por isso que existe o cartão
"sem estoque para venda": produto que aparece no sistema mas não pode ser vendido.

**Segregação.** Um lote vencido sai da conta de disponível **no instante em que vence**, sem esperar
ninguém agir. Marcar como segregado registra o ato físico de tirar da gôndola e levar para a área de
segregação, e gera movimentação. As duas coisas se complementam: o cálculo não espera por você, e o
registro não finge que você já agiu.

**Divergência.** Contagem diferente do esperado **não corrige o estoque**. Ela abre uma divergência,
e só um ajuste de inventário confirmado — com data e motivo — altera a quantidade. Se contar errado,
nada se perde.

**Nada é inventado.** Todo formulário aponta campo por campo o que falta e bloqueia o salvamento até
você completar. Antes de gravar, uma tela mostra exatamente o que vai ser salvo. Entrada com
validade já vencida ou muito curta gera aviso antes de confirmar. Código de lote repetido com
validade diferente vira conflito: o app pergunta em vez de escolher por você.

**Degustação não é perda.** Sai do estoque, mas aparece em separado nos relatórios. Somar as duas
coisas esconderia o número que interessa: quanto realmente estragou.

---

## Câmera e leitura automática

Não existe "a câmera": existem três, porque cada uma responde uma pergunta diferente.

| Onde | Lê | Para quê |
|---|---|---|
| **O que é isso?** (painel) | código de barras | "que produto é este?" — mostra os lotes, validades e quantidades, ou oferece cadastrar |
| **Ler etiqueta** (dentro de Registrar entrada) | texto da etiqueta | preenche lote e validade do que está chegando |
| **Conferir** (botão Contar) | código de barras | aponta e o app já está no campo de quantidade daquele lote, mostrando o esperado |

**Entrada em rajada**, para dia de entrega: a câmera fica aberta, você fotografa etiqueta atrás de
etiqueta, cada uma vira um cartão numa fila, e **só no fim** aparece a revisão de todas juntas. Nada
é gravado antes disso.

### O que o app entende sozinho

O leitor de etiqueta reconhece os formatos que as embalagens brasileiras usam:

`VAL 31/12/26` · `VALIDADE: 31.12.2026` · `CONSUMIR (PREFERENCIALMENTE) ANTES DE` · `VENCIMENTO` ·
`VÁLIDO ATÉ` · `31 DEZ 26` · `VAL 311226` · `VAL 12/2026` (vira o último dia do mês) ·
`FAB 01/01/26 VAL 01/01/27` · `FABRICAÇÃO 01/01/2026 + VALIDADE 12 MESES` (calcula) ·
`LOTE: 2504A` · `L 2504A` · `LOTE Nº A1234` · peso (`500 G`, `1,5 KG`) · código de barras EAN.

### A câmera nunca salva sozinha

Essa é a continuação direta da regra "não inventar dados", e aqui ela pesa mais:

- A câmera **preenche um rascunho e para**. Quem grava é você.
- **Campo duvidoso fica em branco**, não chutado. Validade errada é pior que validade vazia: ela
  corrompe silenciosamente a ordem do PVPS e contamina o painel inteiro sem ninguém perceber.
- O app **segura e pergunta** quando: a validade lida já passou ou está a mais de 3 anos; o ano veio
  com dois dígitos (mostra expandido: "entendi 31/12/2026"); havia letra no lugar de número; havia
  duas datas sem rótulo; o lote já existe com validade diferente. Também avisa quando a validade
  está muito fora do padrão dos outros lotes daquele produto.
- **"Não consegui ler → digitar"** está sempre a um toque, inclusive se você negar a permissão de
  câmera.
- A **foto da etiqueta fica guardada junto com o lote**. Seis meses depois, numa divergência, você
  abre a foto e confere a etiqueta original em vez de discutir de memória.

### Ajuda por IA — opcional, desligada por padrão

Quando o OCR lê o texto mas o interpretador não acha validade ou lote, aparece o botão **"pedir
ajuda da IA"**. Ela devolve campos separados, cada um com seu botão "usar" — nada é aplicado em
bloco, e o que você não aceitar não entra.

Para usar, vá em **Ajustes → Ajuda por IA**, ligue e cole a sua chave da API. Sobre a chave:

- Ela fica **só neste aparelho**. Não está dentro do APK, não está no repositório e não entra no
  backup JSON. Chave compilada dentro de um APK é chave publicada, porque qualquer um consegue abrir
  o arquivo.
- Com a ajuda desligada, ou sem chave, **o app não abre conexão nenhuma**.
- Quando você usa, o texto lido da etiqueta é enviado para a API. É a única informação que sai do
  aparelho, e só nesse momento.

### Permissões que o app pede

| Permissão | Quando é usada |
|---|---|
| Câmera | só quando você abre um leitor. O reconhecimento roda no aparelho. |
| Notificação | só pelo aviso da manhã, se você ligar. |
| Internet | só pela ajuda por IA, se você ligar e colar a chave. |

### O que o app deliberadamente **não** faz

- **Não conta produto na prateleira por foto.** Parece mágico e é armadilha: caixa empilhada,
  embalagem brilhante e sobreposição dão contagem errada — e contagem errada aqui gera divergência
  fantasma, exatamente o problema que o sistema existe para evitar.
- **Não salva direto da câmera sem revisão.** Economiza dois segundos e custa a confiança no estoque
  inteiro.
- **Não faz OCR contínuo no vídeo.** Gasta bateria e treme. Uma foto parada, com lanterna e toque
  para focar, lê melhor.

---

## Backup — leia isto

Os dados ficam **só neste aparelho**. Não há nuvem, não há sincronização, não há como recuperar nada
se o celular quebrar ou for perdido.

Em **Ajustes → Backup → Exportar backup** o app gera um arquivo `.json` que você salva onde quiser
(Downloads, Google Drive, e-mail para você mesmo). É texto legível, dá para abrir e conferir.
**Importar backup substitui tudo** — ou o arquivo inteiro entra, ou nada muda.

Faça um backup de tempos em tempos, e sempre antes de usar "Limpar tudo".

As **fotos não entram no JSON** — o backup guarda só o nome do arquivo. Elas ficam na área privada
do app, no aparelho. Restaurar um backup em outro celular traz todo o estoque; as fotos ficam para
trás.

---

## Roteiro para experimentar antes de usar de verdade

Em **Ajustes → Dados → Carregar dados de exemplo**. São produtos inventados, calculados a partir da
data de hoje para que todas as faixas do painel apareçam preenchidas. Depois:

1. Abra o painel e veja o lote vencido no topo.
2. **Registrar reposição** de um produto que tenha estoque no depósito — repare que o app escolhe o
   lote que vence primeiro.
3. **Registrar contagem** de um lote com número diferente do esperado, e confira que o estoque
   **não** mudou.
4. **Mostrar divergências** e resolva com o ajuste de inventário.
5. **Registrar perda** por vencimento no lote vencido.
6. **Fechar conferência do dia** e veja o que ficou sem contagem.
7. **Gerar relatório semanal**.

Depois use **Limpar tudo** e cadastre os produtos reais da sua seção.

---

## Para quem for mexer no código

```
core/    regras de estoque e validade, em Kotlin puro, sem nada de Android
app/     banco (Room), telas (Compose) e o resto do aplicativo
```

O `core` é um build Gradle **independente** de propósito: ele não depende do Android, então compila
e roda os testes em qualquer máquina com Java, sem SDK do Android instalado.

```bash
cd core && ../gradlew test     # 128 testes das regras de negócio
./gradlew assembleRelease      # APK assinado (exige o SDK do Android)
```

O GitHub Actions roda os testes do `core` antes de tudo: se uma regra de validade, de PVPS ou de
divergência quebrar, o APK nem chega a ser compilado.

### Onde está cada regra

| Arquivo | Responsabilidade |
|---|---|
| `core/.../regras/ClassificacaoValidade.kt` | as seis faixas e as bordas exatas |
| `core/.../regras/Pvps.kt` | ordenação e consumo em cascata entre lotes |
| `core/.../regras/Estoque.kt` | físico, exposto, depósito e disponível para venda |
| `core/.../regras/Operacoes.kt` | entrada, reposição, saída, baixa, transferência, segregação, ajuste |
| `core/.../regras/Contagens.kt` | contagem, divergência e fechamento do dia |
| `core/.../regras/Validacao.kt` | campos obrigatórios e a tela de confirmação |
| `core/.../painel/Painel.kt` | os oito cartões prioritários |
| `core/.../relatorio/Relatorios.kt` | semanal e mensal |
| `core/.../leitura/InterpretadorDeEtiqueta.kt` | os formatos de etiqueta e as guardas da leitura |
| `core/.../leitura/GuardasDeLeitura.kt` | lote repetido, validade fora do padrão do produto |

O interpretador de etiqueta é **Kotlin puro** de propósito: é a peça de que a câmera inteira depende,
então ela é testada sem aparelho, sem câmera e sem rede. A camada Android por cima dele
(`app/.../camera/`) só liga a câmera e entrega texto.
