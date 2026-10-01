-- =====================================================================
-- Schema do banco de dados - App de Escalas (Coroinhas, Acólitos e
-- Cerimoniários)
--
-- Compatibilidade: escrito em SQL padrão para funcionar tanto em SQLite
-- (uso local/testes) quanto em PostgreSQL / Supabase (produção,
-- multiusuário) com o mínimo de ajuste possível.
--
-- Diferenças a ajustar na migração para Postgres, quando chegar a hora:
--   - INTEGER PRIMARY KEY AUTOINCREMENT  ->  GENERATED ALWAYS AS IDENTITY
--   - TEXT (datas/timestamps)            ->  TIMESTAMPTZ / DATE
-- Nada na estrutura das tabelas ou relacionamentos muda.
-- =====================================================================

PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------
-- IGREJA
-- Cada aba da planilha original = uma igreja.
-- ---------------------------------------------------------------------
CREATE TABLE igreja (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    nome            TEXT NOT NULL UNIQUE,   -- ex: "São José" (nome da aba)
    titulo_escala   TEXT,                   -- ex: "Escala da Comunidade São José - Setembro de 2026"
    coordenadores   TEXT,                   -- texto livre, ex: "Gabrielly Cardoso e Gustavo Henrique"
    criado_em       TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ---------------------------------------------------------------------
-- PERIODO_ESCALA
-- Representa "uma planilha enviada" (ex: Setembro/2026). Guardar isso
-- como entidade própria (em vez de só um campo mês/ano solto) permite:
--   - reprocessar/reenviar um mês sem apagar o histórico dos meses
--     anteriores
--   - futuramente comparar quem serviu mais em quais meses
-- ---------------------------------------------------------------------
CREATE TABLE periodo_escala (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    igreja_id       INTEGER NOT NULL REFERENCES igreja(id) ON DELETE CASCADE,
    referencia      TEXT NOT NULL,          -- ex: "Setembro de 2026" (texto livre vindo da planilha)
    arquivo_origem  TEXT,                   -- nome do .xlsx que originou esse período (auditoria)
    importado_em    TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE (igreja_id, referencia)
);

-- ---------------------------------------------------------------------
-- POSTO
-- Um "posto de serviço" dentro de um período de uma igreja (ex: linha
-- "Sineta 1" na planilha). Como a mesma função (ex: "Sineta") pode se
-- repetir várias vezes numa aba, o posto NÃO é identificado só pelo
-- nome da função — cada linha da planilha vira um posto próprio, na
-- ordem em que aparece.
-- ---------------------------------------------------------------------
CREATE TABLE posto (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    periodo_escala_id   INTEGER NOT NULL REFERENCES periodo_escala(id) ON DELETE CASCADE,
    funcao              TEXT NOT NULL,      -- ex: "Missal", "Cruz", "Sineta 1"
    ordem_na_planilha   INTEGER NOT NULL    -- preserva a ordem original das linhas
);

-- ---------------------------------------------------------------------
-- ESCALACAO
-- Uma célula preenchida da planilha: "nesta data, esta pessoa está
-- neste posto". Pessoas ainda são só texto (nome), sem vínculo com
-- conta de usuário — esse vínculo é um campo separado (pessoa_user_id),
-- deixado nulo por enquanto, para adicionar login sem precisar migrar
-- a tabela depois.
-- ---------------------------------------------------------------------
CREATE TABLE escalacao (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    posto_id        INTEGER NOT NULL REFERENCES posto(id) ON DELETE CASCADE,
    data_servico    TEXT NOT NULL,          -- texto original da planilha, ex: "Domingo 06"
    pessoa_nome     TEXT NOT NULL,          -- nome tal como veio da planilha
    pessoa_user_id  INTEGER,                -- reservado para o futuro vínculo com login (NULL por enquanto)
    UNIQUE (posto_id, data_servico)         -- um posto só tem uma pessoa por data
);

-- ---------------------------------------------------------------------
-- COORDENADOR
-- Login PROVISÓRIO: apenas nome + uma "chave de acesso" simples (sem
-- senha de verdade, sem provedor social). Isso é o suficiente para
-- desenvolver e testar o Painel do Coordenador agora; na versão final
-- essa tabela é substituída por um provedor de autenticação real
-- (Google/Apple via NextAuth ou Supabase Auth), mas a ideia de "ligar
-- um coordenador a uma ou mais igrejas" continua a mesma.
-- ---------------------------------------------------------------------
CREATE TABLE coordenador (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    nome            TEXT NOT NULL,
    chave_acesso    TEXT NOT NULL UNIQUE,   -- código simples digitado no login provisório
    is_admin        INTEGER NOT NULL DEFAULT 0, -- 1 para administradores autorizados a gerenciar acessos
    criado_em       TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Liga um coordenador às igrejas que ele pode editar (um coordenador
-- pode cuidar de mais de uma igreja, como já acontece na planilha real).
CREATE TABLE coordenador_igreja (
    coordenador_id  INTEGER NOT NULL REFERENCES coordenador(id) ON DELETE CASCADE,
    igreja_id       INTEGER NOT NULL REFERENCES igreja(id) ON DELETE CASCADE,
    PRIMARY KEY (coordenador_id, igreja_id)
);

-- ---------------------------------------------------------------------
-- Índices de apoio às consultas mais comuns do app:
--   - "onde/quando eu sirvo este mês" (busca por nome)
--   - "quem serve hoje em todas as igrejas" (busca por data)
-- ---------------------------------------------------------------------
CREATE INDEX idx_escalacao_pessoa_nome ON escalacao(pessoa_nome);
CREATE INDEX idx_escalacao_data_servico ON escalacao(data_servico);
CREATE INDEX idx_posto_periodo ON posto(periodo_escala_id);
CREATE INDEX idx_periodo_igreja ON periodo_escala(igreja_id);
