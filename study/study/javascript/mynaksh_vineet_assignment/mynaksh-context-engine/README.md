# MyNaksh Personalized AI Context Engine

## Setup

Step1: Install dependencies:
npm install
Step2: Create environment file:
cp .env.example .env
Step3: Run development server:
npm run dev

## Endpoints

### POST /personalize

Request:
{
"userId": "user_101",
"question": "Should I consider changing my job in the next few months?"
}
personalize curl command:
curl -X POST http://localhost:3000/personalize -H "Content-Type: application/json" -d "{\"userId\":\"user_101\",\"question\":\"Should I consider changing my job in the next few months?\"}"

### POST /debug/personalization

Request:
{
"userId": "user_101",
"question": "Should I consider changing my job in the next few months?"
}
debug curl command:
curl -X POST http://localhost:3000/debug/personalization -H "Content-Type: application/json" -d "{\"userId\":\"user_101\",\"question\":\"Should I consider changing my job in the next few months?\"}"

## LLM Configuration

The application uses a mock LLM provider by default.
LLM_PROVIDER=mock
A real OpenAI-compatible provider can be enabled using:
LLM_PROVIDER=openai
OPENAI_API_KEY=your_key
The application falls back to the mock provider if no API key is configured.

## Assumptions

- Context selection is deterministic and configuration-driven.
- The upstream data is static for a user , so we can use inmemory cache for demonstration

## Trade-offs

- Rule-based intent detection: A deterministic classifier is easier to test and explain.An LLM-based classifier could be added later.
- Single Httpclient and retries and fallback mechanism for each upstream for code simplicity

## Intentionaly simplified

- Using the Inmemory cache
- Error handling the error from the adapter other then for user service if the adapter response is not available picking empty context and still making the llm call
- Not Used the production grade global error handling
- Unit Test and api tests

## What i would imporve with another day of developement

- I would refine the intent select would make it ML based, would extract a list of intent from the question and score each intent, and then select multiple primary contexts
- Would handle the different upstream errors seperately wherever our service can retry or gracefully handle the error based on the upstream error code
- would make the perzonalization rules configurable so we can add new intents and context combinations with some code changes
- llm response eval using another llm call

## production concerns intentionally left out

- rate limit and abuse protection as our api contains costly llm calls
- circuit breaker
- not logging the user pii data, logging the actual error at global layer or if we are gracefully handling
- user chat context for continous chat

- /personalize

/ debug

- upstream{}
- identify intent of the user question only fetching single intent based on key matching,
- collect context based on static rules {intent: personaliztionrules{primary,secondary,exclude}} {kundi: kundlivlaues, horosope:horscopevalues }
- building the required context based on intent {personalizationrule {primary:["horoscopecareer", "houses6" ]}} keys -> value, decsison {secletedcontectvalues,excludecontextkey, language, confidence}
- gaurdrails
- build the llm promt -
- make llm call for the llm response

- we can first call the user cache
- intent classification - {light weight classifcation llm, our local llm + cache for already recognized question predict my future career }
- global cache based on userid as key all upstream
- we can call only upstream service {}
- garud rails and prompt checking

- while calling llm with the whole prompt { we would sending only 5-10 last messages, for this current chat summary + user global chat short summary, current question context }\
- current chat on nosql , we sotre the current chat summary sepertately in memory , old global chat store summary ,

- gaurd rails and llm response check before sending to user evaluation basedon llm
- we must check the llm cost , or if we can reduce the llm calls
- circuit breaker for upstream services and rate limit at the intial edge
- cofniguration in db server through redis
