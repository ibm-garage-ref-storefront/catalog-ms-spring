package catalog.models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ItemService {
	private static final Logger logger = LoggerFactory.getLogger(ItemService.class);
    @Value("${elasticsearch.url}")
    private String url;

    @Value("${elasticsearch.user}")
    private String user;

    @Value("${elasticsearch.password}")
    private String password;

    @Value("${elasticsearch.index}")
    private String index;

    @Value("${elasticsearch.doc_type}")
    private String doc_type;

    private OkHttpClient client;

    // Constructor
    public ItemService() {
        client = new OkHttpClient();
    }

    // Get all rows from database
    @HystrixCommand(fallbackMethod = "failGood")
    public List<Item> findAll() throws JSONException {
        List<Item> list;
        final String req_url = url + "/" + index + "/" + doc_type + "/_search";
        final Response response = perform_request(req_url);

        try {
            logger.info(String.format("Got response:%s", response.toString()));
            list = getItemsFromResponse(response);

        } catch (IOException e) {
            // Just to be safe
            list = null;
            logger.error(e.getMessage(), e);
        }

        return list;
    }
    
    public List<Item> failGood(){
    	// test fallback
    	Item item = new Item("test","test",0,"test","test",0);
    	List<Item> list = new ArrayList<Item>();
    	list.add(item);
    	return list;
    }

    // Get all rows from database
    public Item findById(long id) throws JSONException {
        Item item = null;
        String req_url = url + "/" + index + "/" + doc_type + "/" + id;
        Response response = perform_request(req_url);

        try {
            JSONObject resp = new JSONObject(response.body().string());
            logger.debug("Response: " + resp.toString());

            if (resp.has("found") && resp.getBoolean("found") == true) {
                JSONObject itm = resp.getJSONObject("_source");
                logger.debug("Found item: " + id + "\n" + itm);
                item = new ObjectMapper().readValue(itm.toString(), Item.class);
            }

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        return item;
    }

    // Get all rows from database
    public List<Item> findByNameContaining(String name) throws JSONException {
        List<Item> list;
        String req_url = url + "/" + index + "/" + doc_type + "/_search?q=name:" + name;
        Response response = perform_request(req_url);

        try {
            list = getItemsFromResponse(response);

        } catch (IOException e) {
            // Just to be safe
            list = null;
            logger.error(e.getMessage(), e);
        }

        return list;
    }

    private Response perform_request(String req_url) {
        Response response;
        logger.debug("req_url: " + req_url);

        try {
            Request.Builder builder = new Request.Builder()
                    .url(req_url)
                    .get()
                    .addHeader("content-type", "application/json");

            if (user != null && !user.equals("") && password != null && !password.equals("")) {
                logger.debug("Adding credentials to request");
                builder.addHeader("Authorization", Credentials.basic(user, password));
            }

            Request request = builder.build();
            response = client.newCall(request).execute();

        } catch (IOException e) {
            // Just to be safe
            response = null;
            System.out.println(e);
        }

        return response;
    }

    private List<Item> getItemsFromResponse(Response response) throws IOException, JSONException {
        List<Item> list = new ArrayList<Item>();

        JSONObject resp = new JSONObject(response.body().string());
        if (!resp.has("hits")) {
        	// empty cache
        	return list;
        }
        
        JSONArray hits = resp.getJSONObject("hits").getJSONArray("hits");

        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.getJSONObject(i).getJSONObject("_source");
            Item item = new ObjectMapper().readValue(hit.toString(), Item.class);
            list.add(item);
        }

        return list;
    }
}