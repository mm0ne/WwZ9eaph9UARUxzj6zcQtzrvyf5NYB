import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.annotations.*;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.time.Duration;
import java.util.List;


import org.testng.Assert;

public class PeriplusCartTest {
    WebDriver driver;
    WebDriverWait wait;

    private String username;
    private String password;
    private final String siteUrl = "https://periplus.com";
    private final Duration timeout = Duration.ofSeconds(10);

    @BeforeClass
    void setup() {
        
        WebDriverManager.chromedriver().setup();
        wait = new WebDriverWait(driver, timeout);
        
        Dotenv dotenv = Dotenv.load();  
        username = dotenv.get("PERI_USERNAME");
        password = dotenv.get("PERI_PASSWORD");
    }

    @BeforeTest
    void setupDriver() {
        ChromeOptions options = new ChromeOptions();
        //options.addArguments("--headless")
        driver = new ChromeDriver(options);
    }

    @AfterTest
    void tearDown() {
        driver.quit();
    }

    void login() throws RuntimeException {
        String loginButtonId = "button-login";
        String searchBarId = "filter_name";
        driver.get(siteUrl + "/account/Login");
        try {

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(loginButtonId)));
        } catch (NoSuchElementException error) {
            Assert.fail("Login failed: Can't find login button element with id '" + loginButtonId + "'");
        }

        driver.findElement(By.name("email")).sendKeys(username);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.id(loginButtonId)).click();
        try {

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(searchBarId)));
        } catch (NoSuchElementException error) {

            if (driver.findElements(By.id("warning")).size() > 0)
                Assert.fail("Login failed: Invalid Credentials");
            else
                Assert.fail("Login failed: Unknown Error");
        }

    }

    void logout() {
        driver.get(siteUrl + "/_index_/Logout");
        try {

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("info-title1")));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("info-title2")));

            WebElement logoutNotice = driver.findElement(By.id("content"));

            Assert.assertTrue(logoutNotice.getText().contains("You have been logged off your account."));

        } catch (NoSuchElementException error) {
            Assert.fail("Logout failed: Unknown Error");
        }
    }

    List<WebElement> searchItems(String q, int itemCount) throws NoSuchElementException {

        if (itemCount < 1)
            itemCount = 1;

        String productCardId = "single-product";
        WebElement searchBar = driver.findElement(By.id("filter_name"));
        WebElement searchBarSubmitButton = driver.findElement(By.className("btnn"));
        searchBar.sendKeys(q);
        searchBarSubmitButton.click();

        try {

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className(productCardId)));
        } catch (NoSuchElementException error) {
            Assert.fail("Search Failed: Couldn't find any item matching query '" + q + "'");
        }

        List<WebElement> products = driver.findElements(By.className(productCardId));

        if (itemCount > products.size())
            return products;
        else
            return products.subList(0, itemCount);
    }

    String addItemToCart(String productUrl, boolean isNegativeCase) {
        driver.get(productUrl);
        try {

            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("preloader")));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("btn-add-to-cart")));
        } catch (NoSuchElementException error) {
            Assert.fail("Couldn't load product detail page for '" + productUrl + "'");
        }
        List<WebElement> buttons = driver.findElements(By.className("btn-add-to-cart"));

        for (WebElement button : buttons) {
            if (button.getText().toLowerCase().contains("add to cart")) {
                button.click();
                break;
            }
        }            

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("modal-text")));

        String modalText = driver.findElement(By.className("modal-text")).getText();

        if (!isNegativeCase && modalText.toLowerCase().contains("success add to cart")) {
            WebElement link = driver.findElement(By.xpath("//head//link"));
            return link.getDomAttribute("href");
        } else if (isNegativeCase && modalText.toLowerCase().contains("your desired qty is not available")) {
            WebElement link = driver.findElement(By.xpath("//head//link"));
            return link.getDomAttribute("href");
        } else {
            Assert.fail("Couldn't add product to cart; " + modalText);
            return null;
        }

    }

    WebElement checkCartContainsItem(String itemUrl) {
        driver.get(siteUrl + "/checkout/cart");

        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("basket")));
        } catch (NoSuchElementException error) {
            Assert.fail("Cart is Empty");
        }

        
        List<WebElement> carts = driver.findElements(By.className("row-cart-product"));
        
        for (WebElement item: carts){
            String link = item.findElement(By.tagName("a")).getDomAttribute("href");
            if (link.contains(itemUrl)) {
                return item;
            }
        }
        Assert.fail("Product with URL " + itemUrl + " Can't be found in the cart");
        return null;
        

    }

    void checkItemIsNotInCart(String itemUrl) {
        driver.get(siteUrl + "/checkout/cart");
        boolean cartIsEmpty = driver.findElements(By.xpath("//*[contains(text(), 'Your shopping cart is empty')]"))
                .size() > 0;

        if (cartIsEmpty)
            return;

        List<WebElement> carts = driver.findElements(By.className("row-cart-product"));

        for (WebElement item : carts) {
            String link = item.findElement(By.tagName("a")).getDomAttribute("href");

            Assert.assertTrue(!link.contains(itemUrl), "Found OUT OF STOCK item " + itemUrl + " in the cart");
        }

    }
    void deleteItemFromCart(String productUrl){
        WebElement item = checkCartContainsItem(productUrl);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("preloader")));
        item.findElement(By.className("btn-cart-remove")).click();
        checkItemIsNotInCart(productUrl);
    }

    @Test
    void AddAvailableStockItemToCart() {
        login();
        String searchTerm = "haruki murakami";
        WebElement product = searchItems(searchTerm, 1).get(0);
        String productUrl = product.findElement(By.xpath("//div[@class='product-img']//a")).getDomAttribute("href");
        String item = addItemToCart(productUrl, false);
        checkCartContainsItem(item);
        logout();
    }

    @Test
    void AddOutOfStockItemToCart() {
        login();
        String productUrl = "https://www.periplus.com/p/PER_Pausbundling/whispers-of-hope-bundling-jilid-1-and-2";
        String item = addItemToCart(productUrl, true);
        checkItemIsNotInCart(item);
        logout();
    }

    @Test
    void DeleteItemFromCart(){
        login();
        String productUrl = "http://www.periplus.com/p/9780008652609/it-s-complicated-confessions-of-messy-modern-love";
        String item = addItemToCart(productUrl, false);
        deleteItemFromCart(item);
    }
}
