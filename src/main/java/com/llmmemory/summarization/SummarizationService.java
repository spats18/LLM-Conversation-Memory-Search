package com.llmmemory.summarization;

@Service
public class SummarizationService {
    private final OpenAiConfig openAiConfig;
    private final RestTemplate restTemplate;

    SummarizationService(OpenAiConfig openAiConfig, RestTemplate restTemplate) {
        this.openAiConfig = openAiConfig;
        this.restTemplate = restTemplate;
    }

    public String summarize(String rawContent) throws SummarizationException{
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");

        String systemPrompt = "You are a helpful assistant that summarizes conversations. " +
                "Your task is to read the following conversation and provide a concise summary of the key points discussed." +
                "Focus on the main topics, decisions made, and any action items mentioned. " +
                "Avoid including minor details or off-topic discussions. " +
                "The summary should be clear and easy to understand, capturing the essence of the conversation without losing important information.";

        message.put("content", systemPrompt + "\n\nConversation:\n" + rawContent);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiConfig.getModel());
        requestBody.put("messages", Collections.singletonList(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiConfig.getKey());
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForEntity(openAiConfig.getUrl(), requestEntity, Map.class).getBody();
            if (response == null || !response.containsKey("choices") || ((List<?>) response.get("choices")).isEmpty()) {
                throw new SummarizationException("Invalid response from OpenAI API: " + response);
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map messageResponse = (Map) choices.get(0).get("message");
            return (String) messageResponse.get("content");

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            LoggerFactory.getLogger(SummarizationService.class).error("OpenAI API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new SummarizationException("Error calling OpenAI API: " + e.getMessage());
        } catch (RestClientException e) {
            LoggerFactory.getLogger(SummarizationService.class).error("Error calling OpenAI API: {}", e.getMessage(), e);
            throw new SummarizationException("Error calling OpenAI API: " + e.getMessage());
        }
    }
}
