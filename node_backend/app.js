/**
 * Project: IntrusionDetector Bot 
 * Author: Ujjwal Krishnamurthi
 * Date: 10 May 2018 
 * Program File: app.js
 * Description: This is the main backend file, and it updates the user to any changes made on Firebase by the app. Once the user
 *              authenticates, the app automatically notifies the user that movement was detected, and provides the user with an 
 *              image of the movement. The user can also ask for help or assistance, request a number of images, or cancel the service
 *              altogether, through the natural language processing done by the app. */

//"use strict"; //Not super necessary in development, but necessary production constraint
var restify = require('restify');
var builder = require('botbuilder');
var botbuilder_azure = require("botbuilder-azure");
var admin = require('firebase-admin');
var serviceAccount = require('./intrusiondetectorprivatekey.json');
var XMLHttpRequest = require('xhr2');
//require('dotenv-extended').load();

var config = {
    credential: admin.credential.cert(serviceAccount),
    databaseURL: "https://intrusiondetector-bbba9.firebaseio.com"
};
admin.initializeApp(config);

var database = admin.database();
var storage = admin.storage();
var imgsString;

var server = restify.createServer();
server.listen(process.env.port || process.env.PORT || 3978, function () {
   console.log('%s listening to %s', server.name, server.url); 
});
  
var connector = new builder.ChatConnector({
    appId: process.env.MicrosoftAppId,
    appPassword: process.env.MicrosoftAppPassword,
    openIdMetadata: process.env.BotOpenIdMetadata 
});

server.post('/api/messages', connector.listen());
var tableName = 'botdata';
var azureTableClient = new botbuilder_azure.AzureTableClient(tableName, process.env.AzureWebJobsStorage);
var tableStorage = new botbuilder_azure.AzureBotStorage({ gzipData: false }, azureTableClient);

var bot = new builder.UniversalBot(connector, function (session, args) {
    session.send('Sorry, I did not understand \'%s\'. Try sending the command \'Help\' to explore my full functionality.', session.message.text);
});

bot.set('storage', tableStorage);

var luisAppId = process.env.LuisAppId;
var luisAPIKey = process.env.LuisAPIKey;
var luisAPIHostName = process.env.LuisAPIHostName || 'westus.api.cognitive.microsoft.com';

const LuisModelUrl = 'https://' + luisAPIHostName + '/luis/v2.0/apps/' + luisAppId + '?subscription-key=' + luisAPIKey;

var recognizer = new builder.LuisRecognizer(LuisModelUrl);
bot.recognizer(recognizer);

var uuid_regex = new RegExp(/^UUID: +[a-zA-Z0-9]+$/);
var UUID;

function onLoad(callback) {
    var cancelled = false;
    return {
        getCancelled : function() {return cancelled; },
        setCancelled : function(temp) {cancelled = temp; callback(cancelled); }
    };
}
var userRef;
var manager;

bot.dialog('SignUpDialog', 
    function(session) {
        //The format is "UUID: hexstring"
        UUID = session.message.text.split(':')[1].trim();
        session.send('UUID checked, firebase configured.');
        checkStorage(UUID,  function(blob, imgDate, url) {
            console.log('Starting DetectedMovement Dialog');
            session.beginDialog('DetectedMovementDialog', {
                imgDate: imgDate,
                url: url
            });
            session.endDialog();
        });
        session.endDialog();
    }
).triggerAction({
    matches: uuid_regex
});

bot.dialog('HelpDialog', function(session) {
    session.send("Hey there, I heard you needed help. This is the Intrusion Detector bot. You can ask me questions about images from your home, " + 
                "if movement was detected, and I'll also send you notifications. To leave, please type the phrase \"Cancel\".");
    session.endDialog();            
}).triggerAction({
    matches: 'Help'
});

bot.dialog('CancelDialog', [
    function(session) {
        builder.Prompts.confirm(session, 'This will cancel all future messages you will receive from the bot. Are you sure?');
    }, 
    function(session, results) {
        if(results.response) {
            if(manager != undefined) {
                manager.setCancelled(true);
            }
            session.send('Service terminated.');
            session.endDialog();
        } else {
            session.send('Service not terminated.');
            session.endDialog();
        }
    }
]).triggerAction({
    matches: 'Cancel',
});

bot.dialog('QueryImagesDialog', 
    function(session, args) {
        // Show previous images in a carousel(allow them to show a certain number as an entity, otherwise default is 5)
        var numEntity = builder.EntityRecognizer.findEntity(args.intent.entities, 'Number');
        numEntity = session.message.text.match(/\d+/g).map(Number);
        var num = numEntity[0];
        console.log(num);
        if(num) {
            if(num > 10 || num < 0) {
                num = 5; //Not too many for testing
                session.send("Setting the number of images to default: 5");
            }
        } else {
            num = 5;
        }
        var imgs = grabImg(UUID, num);
        console.log("Images 133: " + imgs);
        var cards = getCards(session, imgs);
        var message = new builder.Message(session).attachmentLayout(builder.AttachmentLayout.carousel).attachments(cards);
        session.send(message);
    }
).triggerAction({
    matches: 'QueryImages' 
});

bot.dialog('DetectedMovementDialog', 
    function(session, args) {
        var text = 'Hi: It seems that we have detected movement in your home–here is an image of it';
        //downloadFile(imageDate, url);
        var card = new builder.HeroCard(session)
                .title('Movement')
                .subtitle('Taken on ' + args.imgDate)
                .text(text)
                .images([builder.CardImage.create(session, args.url)]);
        var msg = new builder.Message(session).addAttachment(card);
        session.send(msg);
        session.endDialog();
    }
);

function checkStorage(UUID, loaded) {
    var reference = 'users/' + UUID + '/'; //Maybe second slash is unnecessary
    userRef = database.ref(reference);
    console.log(reference);
    // Don't need to set Interval or timeout
    userRef.on('value',function(snapshot) {
        console.log(snapshot.val());
        var notifyUser = snapshot.val().notify;
        imgsString = snapshot.val().images;
        if(notifyUser == 'true' || notifyUser == true) {
            // Reset the data for our changes
            var imgURL = snapshot.val().recentImg;
            var imgDate = snapshot.val().lastImgUpload;
            database.ref(reference).update({
                notify: false
            });
            getImg(imgURL, imgDate, loaded);
        }
    });
    manager = onLoad(function(cancelled) {
        if(cancelled) {
            userRef.off('value');
        }
    });
}

function getImg(URL, imgDate, loaded) {
    var imgBlob;
    var xhr = new XMLHttpRequest();
    xhr.responseType = 'blob';
    xhr.onload = function(event) {
        imgBlob = xhr.response;
        if(loaded != undefined) { //Check whether this works
            loaded(imgBlob, imgDate, URL); //If using url, then the xhr is unnecessary
        } else {
            return imgBlob;
        }
    };
    xhr.open('GET', URL);
    xhr.send();
}

function grabImg(UUID, num) {
    var imgURLs = [];
    console.log("ImgString: " + imgsString);
    var imgSplit = imgsString.split(",");
    for(var i = 0; i < num; i++) {
        if(imgSplit.length <= num) {
            // Should output all images
            return -1;
        } else {
            var imgURL = imgSplit[imgSplit.length - 1 - i]; //Will this work?
            // Check what to do if you don't pass in parameters to javascript function
            imgURLs.push(imgURL.replace("[","").replace("]",""));
        }
    }
    console.log(imgURLs);
    return imgURLs;
}

function getCards(session, imgs) {
    var attachments = [];
    for(var img in imgs) {
        console.log("Image URL: %s", imgs[img]);
        attachments.push(
            new builder.HeroCard(session)
            .title('Images')
            .subtitle('User Requested')
            .images([builder.CardImage.create(session, imgs[img].trim())])
        );
    }
    return attachments;
}