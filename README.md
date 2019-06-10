# TCP-in-UDP

### Grupo: Augusto Cesar Munari Joffer e Nathaly Veneruchi Garcia

#### Manual de execução do programa

#### Compilação 
- Na linha de comando entrar na pasta que contenham os arquivos
- Compilar javac *.java

-Executar o programa Server.java
```
Java Server <Nome do arquivo a ser recebido e gravado em disco> 
<Porta UDP do servidor> 
<Tamanho da Janela Transmissor e Receptor em bytes> 
<Probabilidade de um datagrama UDP ser descartado>
```
```
- Em seguida, executar o programa Client.java
Java Client <Nome do arquivo> 
<IPServidor> 
<Porta UDP do servidor> 
<Tamanho da Janela Transmissor e Receptor em bytes> 
<Valor inicial de timeout para retransmissão de um segmento em milisegundos> 
<TCP Maximum Segment Size>
<Deve ser um para usar retransmissão via ACKs duplicados e zero caso contrário> 
<Probabilidade de um datagrama UDP ser descartado>
```
