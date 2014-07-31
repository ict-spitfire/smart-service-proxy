/**
 * Created by olli on 30.06.14.
 */

var path = window.location.href.toString().split(window.location.host)[1];

var semanticEntitiyLayer2Menu = '\
        <a class="item" href="/services/semantic-entities/virtual-sensor-creation">\
            Create Virtual Sensor\
        </a>\
        <a class="item" href="/services/semantic-entities/virtual-sensor-batch-creation">\
            Create Virtual Sensors from XML\
        </a>\
        <a class="item">\
            Create Semantic Entity\
        </a>\
        <a class="item">\
            Create Semantic Entities from XML\
        </a>\
        <a class="item" href="/services/semantic-entities/semantic-entities-editor">\
            Edit Semantic Entities\
        </a>';

var geoViewsLayer2Menu = '\
        <a class="item" href="/services/geo-views/traffic-monitoring">\
            Traffic Monitoring\
        </a>';

//function openTrafficWindow(){
//   console.log("Geklickt!");
//   window.open("/services/geo-views/traffic-monitoring", "_blank", "height=800,width=600,location=0");
//   return false;
//}

$(document).ready(function(){
    if(path.substring(0, 28) == '/services/semantic-entities/'){
        $('#btnSemanticEntities').addClass('active');
    }
    else if(path == '/services/sparql-endpoint'){
        $('#btnSparqlSearch').addClass('active');
    }
    else if(path.substring(0, 20) == '/services/geo-views/'){
        $('#btnGeoViews').addClass('active');
    }
    else if(path == '/'){
        $('#btnHomepage').addClass('active');
    }

    $('#menuLayer2').find('a').each(function(){
        if(path == $(this).attr('href')){
            $(this).addClass('active');
        }
    });
});

$('#header').html(createHeader()).css('margin-bottom', '30px');

function createHeader(){
    return '\
    <h2 class="ui dividing black header">\
        <img class="ui small right floated image" src="/style/images/ssp.png"/>\
    Smart Service Proxy\
    </h2>\
    <div class="ui tiered menu">\
        <div class="menu" id="menuLayer1">\
            <a class="item" href="/" id="btnHomepage">\
                <i class="home icon"></i>Homepage\
            </a>\
            <a class="item" id="btnSemanticEntities">\
                <i class="grid layout icon"></i>Semantic Entities\
            </a>\
            <a class="item" href="/services/sparql-endpoint" id="btnSparqlSearch">\
                <i class="search icon"></i>&nbsp;SPARQL Search\
            </a>\
            <a class="item" id="btnGeoViews">\
                <i class="globe icon"></i>Geo Views\
            </a>\
        </div>\
        <div class="ui sub menu" id="menuLayer2">' +
        getMenuLayer2Content() +
        '</div>\
    </div>';
}

function getMenuLayer2Content(){
    if(path.substring(0, 28) == '/services/semantic-entities/'){
        return semanticEntitiyLayer2Menu;
    }

    else if(path.substring(0, 20) == '/services/geo-views/'){
        return geoViewsLayer2Menu;
    }
    else {
        return '<a class="item">&nbsp</a>';
    }
}

$('#btnSemanticEntities').click(function(){
    $('#menuLayer2').html(semanticEntitiyLayer2Menu)
});

$('#btnGeoViews').click(function(){
    $('#menuLayer2').html(geoViewsLayer2Menu)
});

$('#menuLayer1').find('a').click(function(){
    $('#menuLayer1').find('a').each(function(){
        $(this).removeClass('active');
    });

    $(this).addClass('active');
});
//
//
//$('#menuLayer2').find('a').click(function(){
//    $('#menuLayer2').find('a').each(function(){
//        $(this).removeClass('active');
//    });
//
//    $(this).addClass('active');
//});








