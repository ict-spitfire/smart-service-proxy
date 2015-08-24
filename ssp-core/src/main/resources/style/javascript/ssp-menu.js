/**
 * Created by olli on 30.06.14.
 */

var path = window.location.href.toString().split(window.location.host)[1];

var semanticEntitiyLayer2Menu = '\
        <a class="item" href="/services/virtual-sensor-creation">\
            Create Virtual Sensor\
        </a>\
        <a class="item" href="/services/virtual-sensor-batch-creation">\
            Create Virtual Sensors from XML\
        </a>\
        <a class="item" href="/services/virtual-sensor-directory">\
            List Virtual Sensors\
        </a>\
        <a class="item" href="/services/graph-directory">\
            List Graphs\
        </a>\
        <a class="item" href="/services/resource-directory">\
            List Resources\
        </a>';

var applicationsLayer2Menu = '\
        <a class="item icon" href="/applications/traffic-monitoring">\
            <i class="road icon"></i> Traffic Monitoring\
        </a>';

//function openTrafficWindow(){
//   console.log("Geklickt!");
//   window.open("/services/applications/traffic-monitoring", "_blank", "height=800,width=600,location=0");
//   return false;
//}

$(document).ready(function(){
    if(path.substring(0, 28) == '/services/semantic-entities/'){
        $('#btnSemanticEntities').addClass('active');
    }
    else if(path == '/services/sparql-endpoint'){
        $('#btnSparql').addClass('active');
    }
    else if(path.substring(0, 20) == '/services/applications/'){
        $('#btnApplications').addClass('active');
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
            <a class="item" href="/services/sparql-endpoint" id="btnSparql">\
                <i class="search icon"></i>&nbsp;SPARQL\
            </a>\
            <a class="item" id="btnApplications">\
                <i class="globe icon"></i>Applications\
            </a>\
        </div>\
        <div class="ui sub menu" id="menuLayer2">' +
            getMenuLayer2Content() +
        '</div>\
    </div>';
}

function getMenuLayer2Content(){
    if(path == '/services/sparql-endpoint'){
        return '<a class="item">&nbsp</a>';
    }

    if(path.substring(0, 10) == '/services/'){
        return semanticEntitiyLayer2Menu;
    }

    else if(path.substring(0, 14) == '/applications/'){
        return applicationsLayer2Menu;
    }
    else {
        return '<a class="item">&nbsp</a>';
    }
}

$('#btnSemanticEntities').click(function(){
    $('#menuLayer2').html(semanticEntitiyLayer2Menu)
});


$('#btnApplications').click(function(){
    $('#menuLayer2').html(applicationsLayer2Menu)
});


$('#menuLayer1').find('a').click(function(){
    $('#menuLayer1').find('a').each(function(){
        $(this).removeClass('active');
    });

    $(this).addClass('active');
});









