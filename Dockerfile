# Imagem do axctg3 para servidor. Não compila nada: empacota o jar de produção que o
# scripts/gerar-imagem.ps1 gera antes (gradlew -Pvaadin.productionMode=true bootJar).
# Ver docs/SERVIDOR-TESTE.md.
FROM eclipse-temurin:21-jre

# Fontes para o AWT do Java: os relatórios Jasper usam DejaVu (vem no jar, via
# jasperreports-fonts), mas elementos sem fontName caem na fonte lógica "SansSerif",
# que no Linux precisa de alguma fonte instalada no sistema.
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

# Fuso e locale do Brasil: LocalDate.now() (data de lançamento, vencimentos, NFe) e a
# formatação dos relatórios dependem disso — o padrão da imagem é UTC/en.
ENV TZ=America/Sao_Paulo
ENV JAVA_OPTS="-Xmx1536m -Duser.language=pt -Duser.country=BR -Duser.timezone=America/Sao_Paulo"

# Arquivos do FileStorage (logo da empresa, certificado A1, ...) — montar um volume aqui.
ENV JMIX_CORE_WORKDIR=/opt/axctg3/work

ARG JAR_FILE
COPY ${JAR_FILE} /opt/axctg3/axctg3.jar

EXPOSE 8085
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/axctg3/axctg3.jar"]
