/*
 * List Template
 * License LGPL(您可以在任何地方免费使用,但请不要吝啬您对框架本身的改进)
 * http://www.xidea.org/project/lite/
 * @author jindw
 * @version $Id: template.js,v 1.4 2008/02/28 14:39:06 jindw Exp $
 */

//var TEMPLATE_NS_REG = /^http:\/\/www.xidea.org\/ns\/(?:template|lite)(?:\/core)?\/?$/;
//
//function isTemplateNS(tagName,namespaceURI){
//    return /^c\:|^xmlns\:c$/i.test(tagName) && (namespaceURI=="#" || namespaceURI=="#core" || namespaceURI ==null) || TEMPLATE_NS_REG.test(namespaceURI);
//}

//:core
var Core = {};
function addParsers(){
	var fn = arguments[0];
	var i = arguments.length;
	while(i-->1){
		Core[arguments[i]] = fn
	}
}
addParsers(parseIfTag,"parseIf");
addParsers(parseElseIfTag,"parseElse","parseElseIf","parseElseif","parseElif");

addParsers(parseForTag,"parseFor","parseForeach","parseForEach");
addParsers(parseVarTag,"parseVar","parseSet");
addParsers(parseOutTag,"parseOut");
addParsers(parseChooseTag,"parseChoose");
addParsers(parseDefTag,"parseDef","parseMacro");
addParsers(processIncludeTag,"parseInclude");
addParsers(function(node){
	$log.error("未知标签：",node.tagName,node.ownerDocument.documentURI)
},"parse");

function parseIfTag(node,context,chain){
    var next = node.firstChild;
    var test = getAttributeEL(context,node,'test',true);
    context.appendIf(test);
    if(next){
        do{
            context.parse(next)
        }while(next = next.nextSibling)
    }
    context.appendEnd();
}

function parseElseIfTag(node,context,chain,requireTest){
    var next = node.firstChild;
    if(requireTest != false){
        var test = getAttributeEL(context,node,'test',requireTest == true);
    }
    if(test){
    	context.appendElse(test);
    }else{
    	context.appendElse();
    }
    if(next){
        do{
            context.parse(next)
        }while(next = next.nextSibling)
    }
    context.appendEnd();
}

function parseChooseTag(node,context,chain){
	var next = node.firstChild;
	var first = true;
	var whenTag = node.tagName.split(':')[0];
	var elseTag = whenTag + ':otherwise';
	whenTag += ':when';
    if(next){
        do{
        	if(next.tagName == whenTag){
        		if(first){
        			first = false;
        			parseIfTag(next,context,chain);
        		}else{
		            parseElseIfTag(next,context,chain,true);
        		}
        	}else if(next.tagName == elseTag){
        		parseElseIfTag(next,context,chain,false);
        	}
		}while(next = next.nextSibling)
    }
}

function parseForTag(node,context,chain){
    var next = node.firstChild;
    var items = getAttributeEL(context,node,['items','values','value'],true);
    var var_ = getAttributeText(context,node,['var','id','name','item'],true);
    var status_ = getAttributeText(context,node,'status');
    context.appendFor(var_,items,status_);
    if(next){
        do{
            context.parse(next)
        }while(next = next.nextSibling)
    }
    context.appendEnd();
}
function parseVarTag(node,context,chain){
    var name = getAttributeText(context,node,['name','id'],true);
    var value = getAttributeText(context,node,'value');
    if(value){
    	var value = context.parseText(value,false);
    	if(value.length == 1){
    		value = value[0];
    		if(value instanceof Array){
    			value = value[1];
    		}
    		context.appendVar(name,value);
    	}else{
    		context.appendCaptrue(name);
	        context.appendAll(value)
	        context.appendEnd();
    	}
    }else{
        var next = node.firstChild;
        context.appendCaptrue(name);
        if(next){
            do{
                context.parse(next)
            }while(next = next.nextSibling)
        }
        context.appendEnd();
    }
}

function parseOutTag(node,context,chain){
    var value = getAttributeText(context,node,"value");
    value = context.parseText(value,EL_TYPE);
    context.appendAll(value);
}


/**
 * 
 */
function parseDefTag(node,context,chain){
    var next = node.firstChild;
    var ns = getAttributeText(context,node,'name',true);
    ns = (ns.replace(/^\s+/,'')+'{end').split(/[^\w]+/);
    ns.pop();
    var el = ['{"name":"',ns[0],'","params":['];
    for(var i=1;i<ns.length;i++){
    	if(i>1){
    		el.push(",")
    	}
    	el.push('"',ns[i],'"');
    }
    el.push("]}")
    //prompt('',el.join(''))
    context.appendPlugin(PLUGIN_DEFINE,context.parseEL(el.join('')));
    if(next){
        do{
            context.parse(next)
        }while(next = next.nextSibling)
    }
    context.appendEnd();
}
function processIncludeTag(node,context,chain){
    var var_ = getAttributeText(context,node,'var');
    var path = getAttributeText(context,node,'path');
    var xpath = getAttributeText(context,node,'xpath');
    var name = getAttributeText(context,node,'name');
    var doc = node.ownerDocument || node;
    var parentURI = context.currentURI;
	try{
		if(name){
			var docFragment = doc.createDocumentFragment();
			var next = node.firstChild;
            if(next){
                do{
                    docFragment.appendChild(next)
                }while(next = next.nextSibling)
            }
            context['#'+name] = docFragment;
		}
	    if(var_){
            var next = node.firstChild;
            context.appendVar(var_);
            if(next){
                do{
                    context.parse(next)
                }while(next = next.nextSibling)
            }
            context.appendEnd();
	    }
	    if(path!=null){
	    	if(path.charAt() == '#'){
	    		doc = context['#'+name];
	    		context.currentURI = doc.documentURI;
	    	}else{
		        var url = parentURI?parentURI.replace(/[^\/]*(?:[#\?].*)?$/,path):path;
		        var doc = context.loadXML(url);
	    	}
	    }
	    if(xpath!=null){
	        doc = selectNodes(doc,xpath);
	    }
	    context.parse(doc)
    }finally{
        context.currentURI = parentURI;
    }
}

/**
 * @internal
 */
var stringRegexp = /["\\\x00-\x1f\x7f-\x9f]/g;
/**
 * 转义替换字符
 * @internal
 */
var charMap = {
    '\b': '\\b',
    '\t': '\\t',
    '\n': '\\n',
    '\f': '\\f',
    '\r': '\\r',
    '"' : '\\"',
    '\\': '\\\\'
};

/**
 * 转义替换函数
 * @internal
 */
function charReplacer(item) {
    var c = charMap[item];
    if (c) {
        return c;
    }
    c = item.charCodeAt().toString(16);
    return '\\u00' + (c.length>1?c:'0'+c);
}
function getAttributeEL(context,node,key,required){
    return getAttributeObject(context,node,key,true,required)
}
function getAttributeText(context,node,key,required){
    return getAttributeObject(context,node,key,false,required)
}

function getAttributeObject(context,node,key,isEL,required){
    if(key instanceof Array){
        for(var i=0;i<key.length;i++){
            var value = node.getAttribute(key[i]);
            if(value){
                if(i>0){
                    $log.warn("元素："+node.tagName +"的属性：'" + key[i] +"' 不被推荐；请使用是:'"+key[0]+"'代替");
                }
                key = key[i];
                break;
            }
        }
    }else{
        var value = node.getAttribute(key);
    }
	if(value){
		value = String(value);
		if(isEL){
	         return findFirstEL(context,value);
		}else{
			return value.replace(/^\s+|\s+$/g,'');
		}
	}else if(required){
	    var error = "属性"+key+"为必选属性";
		$log.error(error);
		throw new Error(error);
	}
}
function findFirstEL(context,value){
	var els = context.parseText(value,EL_TYPE);
	var i = els.length;
	while(i--) {
		var el = els[i];
		if(el instanceof Array){//el
		    return el[1];
		}
	}
}